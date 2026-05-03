package id.co.jalin.seconsole.service;

import com.socket.edge.grpc.MetricsBundle;
import com.socket.edge.grpc.SystemInfo;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import id.co.jalin.seconsole.grpc.OsSnapshotMapper;
import id.co.jalin.seconsole.metrics.history.ConsoleSystemHistoryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * System metrics cache — updated event-driven via {@link GrpcMetricsSubscriber.MetricsListener}.
 *
 * <p>Data flow:
 * <ol>
 *   <li>GrpcMetricsSubscriber.onNext() fires when se-core pushes a bundle.</li>
 *   <li>onBundle() maps immediately via OsSnapshotMapper and updates the cache.</li>
 *   <li>History write is dispatched async so the gRPC callback thread is not blocked.</li>
 *   <li>Controllers read current() — zero-cost cache read, no syscalls.</li>
 * </ol>
 */
@Service
public class SystemMetricsService implements GrpcMetricsSubscriber.MetricsListener {

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
            SystemInfo sysInfo = subscriber.getCachedSystemInfo();
            SystemMetricsDto fresh = mapper.toDto(bundle, sysInfo);
            cache.set(new CacheEntry(fresh, true, Instant.now().toEpochMilli(), null));
            CompletableFuture.runAsync(() -> historyService.record(fresh));
        } catch (Exception ex) {
            markUnreachable(ex.getMessage());
            log.warn("System metrics mapping failed: {}", ex.getMessage());
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
            SystemMetricsDto metrics,
            boolean reachable,
            long lastUpdateMillis,
            String lastError) {

        public static CacheEntry empty() {
            return new CacheEntry(null, false, 0L, null);
        }
    }
}
