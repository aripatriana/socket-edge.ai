package id.co.jalin.seconsole.engine;

import com.socket.edge.grpc.MetricsBundle;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto;
import id.co.jalin.seconsole.dto.response.ThreadListDto;
import id.co.jalin.seconsole.engine.history.EngineJvmHistoryService;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import id.co.jalin.seconsole.grpc.JvmSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls the gRPC MetricsBundle from se-core and exposes it as JvmMetricsDto.
 *
 * <p>Data flow:
 * <ol>
 *   <li>GrpcMetricsSubscriber maintains the latest MetricsBundle from the
 *       se-core stream (reconnects automatically on interruption).</li>
 *   <li>poll() reads the bundle each tick, maps it via JvmSnapshotMapper,
 *       and stores the result in an AtomicReference cache.</li>
 *   <li>Controllers read current() — a pure cache read, no gRPC call per
 *       request.</li>
 * </ol>
 *
 * <p>If the gRPC stream has not yet delivered a bundle (se-core not reachable),
 * the cache entry is marked reachable=false so the UI can show "connecting…"
 * rather than an empty state.
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineJvmSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(EngineJvmSnapshotService.class);

    private final GrpcMetricsSubscriber subscriber;
    private final JvmSnapshotMapper mapper;
    private final EngineJvmHistoryService historyService;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>(CacheEntry.empty());

    public EngineJvmSnapshotService(GrpcMetricsSubscriber subscriber,
                                    JvmSnapshotMapper mapper,
                                    EngineJvmHistoryService historyService) {
        this.subscriber = subscriber;
        this.mapper = mapper;
        this.historyService = historyService;
    }

    @Scheduled(fixedDelayString = "${seconsole.engine.jvm.poll-interval-ms:2000}")
    public void poll() {
        try {
            MetricsBundle bundle = subscriber.getLatestBundle();
            if (bundle == null || !bundle.hasJvm()) {
                CacheEntry prev = cache.get();
                cache.set(new CacheEntry(prev.metrics(), false,
                        Instant.now().toEpochMilli(), "Waiting for gRPC stream"));
                return;
            }
            JvmMetricsDto metrics = mapper.toDto(bundle.getJvm());
            cache.set(new CacheEntry(metrics, true, Instant.now().toEpochMilli(), null));
            historyService.record(bundle.getJvm());
        } catch (Exception ex) {
            CacheEntry prev = cache.get();
            cache.set(new CacheEntry(prev.metrics(), false, Instant.now().toEpochMilli(), ex.getMessage()));
            log.warn("Engine JVM poll from gRPC failed: {}", ex.getMessage());
        }
    }

    /** Current cached snapshot for dashboard consumption. */
    public CacheEntry current() { return cache.get(); }

    /**
     * Thread list for the Threads tab. gRPC MetricsBundle only carries aggregate
     * thread counts, not per-thread detail — this returns the count with an empty
     * thread list. Per-thread info is not available via the streaming path.
     */
    public ThreadListDto fetchThreads() {
        CacheEntry e = cache.get();
        if (e.metrics() == null) {
            return new ThreadListDto(Instant.now(), 0, Collections.emptyList());
        }
        JvmMetricsDto.ThreadMetrics t = e.metrics().threads();
        return new ThreadListDto(e.metrics().timestamp(), t.liveCount(), Collections.emptyList());
    }

    public record CacheEntry(
            JvmMetricsDto metrics,
            boolean reachable,
            long lastUpdateMillis,
            String lastError
    ) {
        public static CacheEntry empty() {
            return new CacheEntry(null, false, 0L, null);
        }
    }
}
