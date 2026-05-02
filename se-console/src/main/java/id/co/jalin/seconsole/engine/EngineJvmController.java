package id.co.jalin.seconsole.engine;

import id.co.jalin.seconsole.dto.response.JvmMetricsDto;
import id.co.jalin.seconsole.dto.response.ThreadListDto;
import id.co.jalin.seconsole.engine.history.EngineJvmHistoryService;
import id.co.jalin.seconsole.engine.history.EngineJvmSnapshotEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Exposes SE-Core JVM telemetry to the frontend.
 *
 * <p>The response shape is deliberately identical to
 * {@link id.co.jalin.seconsole.controller.ConsoleJvmController} (console's
 * own JVM) — this lets the JVM-SE tab render with the same components the
 * JVM tab uses.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code /metrics} — pure cache read from {@link
 *       EngineJvmSnapshotService#current()}. No outbound engine call.</li>
 *   <li>{@code /history} — range scan against {@code engine_jvm_snapshot}
 *       for chart backfill. Same shape as {@code /api/console/jvm/history}.</li>
 *   <li>{@code /threads} — on-demand with {@code ?details=true}. Expensive,
 *       called only when the Threads sub-tab is opened.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/engine/jvm")
public class EngineJvmController {

    private final EngineJvmSnapshotService snapshotService;
    private final EngineJvmHistoryService historyService;

    public EngineJvmController(EngineJvmSnapshotService snapshotService,
                               EngineJvmHistoryService historyService) {
        this.snapshotService = snapshotService;
        this.historyService = historyService;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<JvmMetricsDto> metrics() {
        EngineJvmSnapshotService.CacheEntry entry = snapshotService.current();
        JvmMetricsDto payload = entry.metrics();
        if (payload == null) {
            // Poller hasn't produced a first sample yet — return 503 so the
            // frontend keeps retrying without crashing on a null deadlock
            // field. Same convention used by the channels snapshot endpoint.
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<EngineJvmSnapshotEntity>> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(historyService.between(from, to));
    }

    @GetMapping("/threads")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<ThreadListDto> threads() {
        return ResponseEntity.ok(snapshotService.fetchThreads());
    }
}
