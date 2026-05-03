package id.co.jalin.seconsole.engine;

import com.socket.edge.grpc.MetricsBundle;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto;
import id.co.jalin.seconsole.dto.response.ThreadListDto;
import id.co.jalin.seconsole.engine.history.EngineJvmHistoryService;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import id.co.jalin.seconsole.grpc.JvmSnapshotMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JVM metrics cache — updated event-driven via {@link GrpcMetricsSubscriber.MetricsListener}.
 *
 * <p>Data flow:
 * <ol>
 *   <li>GrpcMetricsSubscriber.onNext() fires when se-core pushes a bundle.</li>
 *   <li>onBundle() maps immediately via JvmSnapshotMapper and updates the cache.</li>
 *   <li>History write is dispatched async so the gRPC callback thread is not blocked.</li>
 *   <li>Controllers read current() — zero-cost cache read, no syscalls.</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineJvmSnapshotService implements GrpcMetricsSubscriber.MetricsListener {

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

    @PostConstruct
    public void init() {
        subscriber.addListener(this);
    }

    @Override
    public void onBundle(MetricsBundle bundle) {
        try {
            if (!bundle.hasJvm()) {
                markUnreachable("No JVM snapshot in bundle");
                return;
            }
            com.socket.edge.grpc.JvmSnapshot jvm = bundle.getJvm();
            JvmMetricsDto metrics = mapper.toDto(jvm);
            cache.set(new CacheEntry(metrics, true, Instant.now().toEpochMilli(), null));
            CompletableFuture.runAsync(() -> historyService.record(jvm));
        } catch (Exception ex) {
            markUnreachable(ex.getMessage());
            log.warn("Engine JVM mapping failed: {}", ex.getMessage());
        }
    }

    @Override
    public void onDisconnect(String reason) {
        markUnreachable(reason);
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

    private void markUnreachable(String reason) {
        CacheEntry prev = cache.get();
        cache.set(new CacheEntry(prev.metrics(), false, Instant.now().toEpochMilli(), reason));
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
