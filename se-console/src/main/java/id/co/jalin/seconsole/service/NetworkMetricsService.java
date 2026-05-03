package id.co.jalin.seconsole.service;

import com.socket.edge.grpc.MetricsBundle;
import id.co.jalin.seconsole.dto.response.NetworkMetricsDto;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import id.co.jalin.seconsole.grpc.OsSnapshotMapper;
import id.co.jalin.seconsole.metrics.history.ConsoleNetworkHistoryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Network metrics cache — updated event-driven via {@link GrpcMetricsSubscriber.MetricsListener}.
 *
 * <p>Fields not available via gRPC stream (TCP state counts, TCP quality counters,
 * listening ports) are returned as empty — they require /proc/net/* parsing
 * which is only done on the se-core host directly.
 */
@Service
public class NetworkMetricsService implements GrpcMetricsSubscriber.MetricsListener {

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

    @PostConstruct
    public void init() {
        subscriber.addListener(this);
    }

    @Override
    public void onBundle(MetricsBundle bundle) {
        try {
            if (!bundle.hasOs()) {
                markUnreachable("No OS snapshot in bundle");
                return;
            }
            NetworkMetricsDto fresh = mapper.toNetworkDto(bundle);
            cache.set(new CacheEntry(fresh, true, Instant.now().toEpochMilli(), null));
            CompletableFuture.runAsync(() -> historyService.record(fresh));
        } catch (Exception ex) {
            markUnreachable(ex.getMessage());
            log.warn("Network metrics mapping failed: {}", ex.getMessage());
        }
    }

    @Override
    public void onDisconnect(String reason) {
        markUnreachable(reason);
    }

    public CacheEntry current() { return cache.get(); }

    private void markUnreachable(String reason) {
        CacheEntry prev = cache.get();
        cache.set(new CacheEntry(prev.metrics(), false, Instant.now().toEpochMilli(), reason));
    }

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
