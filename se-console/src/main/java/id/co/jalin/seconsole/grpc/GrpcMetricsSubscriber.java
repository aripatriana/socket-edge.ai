package id.co.jalin.seconsole.grpc;

import com.socket.edge.grpc.Empty;
import com.socket.edge.grpc.MetricsBundle;
import com.socket.edge.grpc.SystemInfo;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subscribes to the se-core metrics stream and fans out each bundle to
 * registered {@link MetricsListener}s the moment it arrives (event-driven).
 *
 * On startup, also fetches static SystemInfo from se-core once.
 * If the stream is interrupted, re-subscribes after a configurable delay
 * and notifies listeners via {@link MetricsListener#onDisconnect}.
 */
@Component
public class GrpcMetricsSubscriber {

    private static final Logger log = LoggerFactory.getLogger(GrpcMetricsSubscriber.class);

    /**
     * Implement this interface and call {@link #addListener} to receive
     * metric bundles as they arrive from se-core without polling.
     */
    public interface MetricsListener {
        void onBundle(MetricsBundle bundle);
        default void onDisconnect(String reason) {}
    }

    private final CoreGrpcClient client;
    private final long           reconnectDelayMs;

    private final AtomicReference<MetricsBundle> latestBundle  = new AtomicReference<>();
    private final AtomicReference<SystemInfo>    cachedSysInfo = new AtomicReference<>();
    private final AtomicBoolean                  running       = new AtomicBoolean(true);
    private final List<MetricsListener>          listeners     = new CopyOnWriteArrayList<>();
    private       ScheduledExecutorService       reconnectPool;

    public GrpcMetricsSubscriber(
            CoreGrpcClient client,
            @Value("${seconsole.se-core.reconnect-delay-ms:5000}") long reconnectDelayMs) {
        this.client           = client;
        this.reconnectDelayMs = reconnectDelayMs;
    }

    @PostConstruct
    public void start() {
        reconnectPool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "grpc-reconnect");
            t.setDaemon(true);
            return t;
        });
        fetchSystemInfo();
        subscribe();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (reconnectPool != null) {
            reconnectPool.shutdownNow();
        }
    }

    // ── Listener registration ─────────────────────────────────────────────────

    public void addListener(MetricsListener listener) {
        listeners.add(listener);
    }

    // ── Public accessors (kept for callers that need a point-in-time read) ────

    public MetricsBundle getLatestBundle() {
        return latestBundle.get();
    }

    public SystemInfo getCachedSystemInfo() {
        return cachedSysInfo.get();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void fetchSystemInfo() {
        try {
            SystemInfo info = client.blockingStub()
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getSystemInfo(Empty.getDefaultInstance());
            cachedSysInfo.set(info);
            log.info("SystemInfo fetched from se-core: host={} os={} cpu={}",
                    info.getHostname(), info.getOsName(), info.getCpuModel());
        } catch (Exception e) {
            log.warn("Could not fetch SystemInfo from se-core (will retry on reconnect): {}", e.getMessage());
        }
    }

    private void subscribe() {
        if (!running.get()) return;

        log.info("Subscribing to se-core metrics stream...");
        client.asyncStub().subscribeMetrics(Empty.getDefaultInstance(), new StreamObserver<>() {

            @Override
            public void onNext(MetricsBundle bundle) {
                latestBundle.set(bundle);
                notifyBundle(bundle);
            }

            @Override
            public void onError(Throwable t) {
                if (!running.get()) return;
                Status status = Status.fromThrowable(t);
                String reason = status.getCode() + ": " + t.getMessage();
                log.warn("Metrics stream error ({}), reconnecting in {}ms: {}",
                        status.getCode(), reconnectDelayMs, t.getMessage());
                notifyDisconnect(reason);
                scheduleReconnect();
            }

            @Override
            public void onCompleted() {
                if (!running.get()) return;
                log.info("Metrics stream completed by server, reconnecting in {}ms", reconnectDelayMs);
                notifyDisconnect("Stream completed by server");
                scheduleReconnect();
            }
        });
    }

    private void notifyBundle(MetricsBundle bundle) {
        for (MetricsListener l : listeners) {
            try {
                l.onBundle(bundle);
            } catch (Exception e) {
                log.warn("MetricsListener.onBundle failed: {}", e.getMessage());
            }
        }
    }

    private void notifyDisconnect(String reason) {
        for (MetricsListener l : listeners) {
            try {
                l.onDisconnect(reason);
            } catch (Exception e) {
                log.warn("MetricsListener.onDisconnect failed: {}", e.getMessage());
            }
        }
    }

    private void scheduleReconnect() {
        reconnectPool.schedule(() -> {
            fetchSystemInfo();
            subscribe();
        }, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }
}
