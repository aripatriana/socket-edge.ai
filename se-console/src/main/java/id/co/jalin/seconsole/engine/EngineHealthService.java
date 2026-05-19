package id.co.jalin.seconsole.engine;

import com.socket.edge.grpc.HealthResponse;
import id.co.jalin.seconsole.grpc.CoreGrpcClient;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls gRPC GetHealth on a 5s cadence (configurable) to drive the
 * topbar EngineHealthBadge. Cache is read synchronously from the
 * controller — no engine call on the request path.
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineHealthService {

    private static final Logger log = LoggerFactory.getLogger(EngineHealthService.class);

    private final CoreGrpcClient grpcClient;
    private final EngineProperties props;
    private final AtomicReference<State> state = new AtomicReference<>(State.unknown());

    public EngineHealthService(CoreGrpcClient grpcClient, EngineProperties props) {
        this.grpcClient = grpcClient;
        this.props = props;
    }

    @Scheduled(
            initialDelay = 0,
            fixedDelayString = "${seconsole.engine.health.poll-interval-ms:5000}"
    )
    public void poll() {
        try {
            HealthResponse h = grpcClient.getHealth();
            state.set(new State(true, h.getStatus(), h.getRole(), h.getMode(),
                    Instant.now().toEpochMilli(), null));
        } catch (StatusRuntimeException ex) {
            State prev = state.get();
            state.set(new State(false, prev.status(), prev.role(), prev.mode(),
                    Instant.now().toEpochMilli(), ex.getStatus().toString()));
            log.debug("Engine health poll failed: {}", ex.getStatus());
        } catch (Exception ex) {
            State prev = state.get();
            state.set(new State(false, prev.status(), prev.role(), prev.mode(),
                    Instant.now().toEpochMilli(), ex.getMessage()));
            log.debug("Engine health poll failed: {}", ex.getMessage());
        }
    }

    public State current() { return state.get(); }
    public String baseUrl() { return props.getBaseUrl(); }

    /**
     * Exposed snapshot.
     * @param reachable  true when the last poll returned a response
     * @param status     "OK" / "STANDBY"; may be stale if unreachable
     * @param role       "MASTER" / "SLAVE"; may be stale
     * @param mode       "CLUSTER" / "STANDALONE"; may be stale
     * @param lastPollMs epoch millis of the last poll attempt (success or failure)
     * @param lastError  error message from the most recent failed poll
     */
    public record State(
            boolean reachable,
            String status,
            String role,
            String mode,
            long lastPollMs,
            String lastError
    ) {
        public static State unknown() {
            return new State(false, null, null, null, 0L, null);
        }
    }
}