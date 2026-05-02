package id.co.jalin.seconsole.service;

import com.socket.edge.grpc.MetricsBundle;
import id.co.jalin.seconsole.dto.response.NetworkMetricsDto;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import id.co.jalin.seconsole.grpc.OsSnapshotMapper;
import id.co.jalin.seconsole.metrics.history.ConsoleNetworkHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Network metrics producer — reads from the se-core gRPC stream.
 *
 * <p>Maps OsSnapshot.networks[] to NetworkMetricsDto. Fields not available
 * via gRPC (TCP state counts, TCP quality counters, listening ports) are
 * returned as empty — they require /proc/net/* parsing which is only done
 * on the se-core host directly.
 *
 * <p>Cadence: {@code seconsole.monitoring.network.poll-interval-ms} (default 5000ms).
 */
@Service
public class NetworkMetricsService {

    private static final Logger log = LoggerFactory.getLogger(NetworkMetricsService.class);

    private final GrpcMetricsSubscriber subscriber;
    private final OsSnapshotMapper mapper;
    private final ConsoleNetworkHistoryService historyService;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>(CacheEntry.empty());

    public NetworkMetricsService(GrpcMetricsSubscriber subscriber,
                                 OsSnapshotMapper mapper,
                                 ConsoleNetworkHistoryService historyService) {
        this.subscriber = subscriber;
        this.mapper = mapper;
        this.historyService = historyService;
    }

    @Scheduled(fixedDelayString = "${seconsole.monitoring.network.poll-interval-ms:5000}")
    public void poll() {
        try {
            MetricsBundle bundle = subscriber.getLatestBundle();
            if (bundle == null || !bundle.hasOs()) {
                CacheEntry prev = cache.get();
                cache.set(new CacheEntry(prev.metrics(), false,
                        Instant.now().toEpochMilli(), "Waiting for gRPC stream"));
                return;
            }
            NetworkMetricsDto fresh = mapper.toNetworkDto(bundle);
            cache.set(new CacheEntry(fresh, true, Instant.now().toEpochMilli(), null));
            historyService.record(fresh);
        } catch (Exception ex) {
            CacheEntry prev = cache.get();
            cache.set(new CacheEntry(prev.metrics(), false, Instant.now().toEpochMilli(), ex.getMessage()));
            log.warn("Network metrics poll from gRPC failed: {}", ex.getMessage());
        }
    }

    public CacheEntry current() { return cache.get(); }

    public record CacheEntry(
            NetworkMetricsDto metrics,
            boolean reachable,
            long lastUpdateMillis,
            String lastError) {

        public static CacheEntry empty() {
            return new CacheEntry(null, false, 0L, null);
        }
    }
}
