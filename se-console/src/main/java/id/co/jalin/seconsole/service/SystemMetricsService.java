package id.co.jalin.seconsole.service;

import com.socket.edge.grpc.MetricsBundle;
import com.socket.edge.grpc.SystemInfo;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import id.co.jalin.seconsole.grpc.OsSnapshotMapper;
import id.co.jalin.seconsole.metrics.history.ConsoleSystemHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * System metrics producer — reads from the se-core gRPC stream.
 *
 * <p>Data flow:
 * <ol>
 *   <li>GrpcMetricsSubscriber maintains the latest MetricsBundle from se-core.</li>
 *   <li>poll() reads the bundle each tick and maps it via OsSnapshotMapper.</li>
 *   <li>Controllers read current() — zero-cost cache read, no syscalls.</li>
 * </ol>
 *
 * <p>Cadence: {@code seconsole.monitoring.system.poll-interval-ms} (default 2000ms).
 */
@Service
public class SystemMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsService.class);

    private final GrpcMetricsSubscriber subscriber;
    private final OsSnapshotMapper mapper;
    private final ConsoleSystemHistoryService historyService;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>(CacheEntry.empty());

    public SystemMetricsService(GrpcMetricsSubscriber subscriber,
                                OsSnapshotMapper mapper,
                                ConsoleSystemHistoryService historyService) {
        this.subscriber = subscriber;
        this.mapper = mapper;
        this.historyService = historyService;
    }

    @Scheduled(fixedDelayString = "${seconsole.monitoring.system.poll-interval-ms:2000}")
    public void poll() {
        try {
            MetricsBundle bundle = subscriber.getLatestBundle();
            if (bundle == null || !bundle.hasOs()) {
                CacheEntry prev = cache.get();
                cache.set(new CacheEntry(prev.metrics(), false,
                        Instant.now().toEpochMilli(), "Waiting for gRPC stream"));
                return;
            }
            SystemInfo sysInfo = subscriber.getCachedSystemInfo();
            SystemMetricsDto fresh = mapper.toDto(bundle, sysInfo);
            cache.set(new CacheEntry(fresh, true, Instant.now().toEpochMilli(), null));
            historyService.record(fresh);
        } catch (Exception ex) {
            CacheEntry prev = cache.get();
            cache.set(new CacheEntry(prev.metrics(), false, Instant.now().toEpochMilli(), ex.getMessage()));
            log.warn("System metrics poll from gRPC failed: {}", ex.getMessage());
        }
    }

    public CacheEntry current() { return cache.get(); }

    public record CacheEntry(
            SystemMetricsDto metrics,
            boolean reachable,
            long lastUpdateMillis,
            String lastError) {

        public static CacheEntry empty() {
            return new CacheEntry(null, false, 0L, null);
        }
    }
}
