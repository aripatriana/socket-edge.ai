package id.co.jalin.seconsole.controller;

import id.co.jalin.seconsole.dto.response.MetricsEnvelope;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.metrics.history.ConsoleSystemHistoryService;
import id.co.jalin.seconsole.metrics.history.ConsoleSystemSnapshotEntity;
import id.co.jalin.seconsole.service.SystemMetricsService;
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
 * Console-side system metrics (OS of the host running SE-Console itself).
 *
 * <p>Clean-break replacement for the old {@code /api/system/metrics} endpoint.
 * Scheduler in {@link SystemMetricsService#poll()} is the single writer —
 * this controller never triggers an OSHI probe; it only reads the cache or
 * the history DB.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/console/system/latest} — cache read. Returns 503
 *       if the poller has not produced a first sample yet; the envelope's
 *       {@code reachable} flag tells the UI whether the last probe succeeded.</li>
 *   <li>{@code GET /api/console/system/history?from=...&to=...} — range
 *       scan against {@code console_system_snapshot} for chart backfill.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/console/system")
public class ConsoleSystemController {

    private final SystemMetricsService metricsService;
    private final ConsoleSystemHistoryService historyService;

    public ConsoleSystemController(SystemMetricsService metricsService,
                                   ConsoleSystemHistoryService historyService) {
        this.metricsService = metricsService;
        this.historyService = historyService;
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<MetricsEnvelope<SystemMetricsDto>> latest() {
        SystemMetricsService.CacheEntry e = metricsService.current();
        if (e.metrics() == null) {
            // Poller hasn't produced a first sample yet — match engine
            // convention: the UI should keep retrying rather than render
            // an empty state.
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(new MetricsEnvelope<>(
                e.metrics(), e.reachable(), e.lastUpdateMillis(), e.lastError()));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<ConsoleSystemSnapshotEntity>> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(historyService.between(from, to));
    }
}
