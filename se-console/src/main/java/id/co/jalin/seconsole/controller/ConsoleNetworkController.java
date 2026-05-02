package id.co.jalin.seconsole.controller;

import id.co.jalin.seconsole.dto.response.MetricsEnvelope;
import id.co.jalin.seconsole.dto.response.NetworkConnectionsDto;
import id.co.jalin.seconsole.dto.response.NetworkMetricsDto;
import id.co.jalin.seconsole.metrics.history.ConsoleNetworkHistoryService;
import id.co.jalin.seconsole.metrics.history.ConsoleNetworkSnapshotEntity;
import id.co.jalin.seconsole.service.NetworkConnectionsService;
import id.co.jalin.seconsole.service.NetworkMetricsService;
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
 * Console-side network metrics.
 *
 * <p>Clean-break replacement for {@code /api/network/metrics}. Scheduler in
 * {@link NetworkMetricsService#poll()} is the single writer for the summary
 * snapshot — {@code /latest} is a pure cache read.
 *
 * <p>The per-connection detail ({@code /connections}) remains on-demand.
 * {@link NetworkConnectionsService} still enumerates the OS connection
 * table synchronously per request, because the list can be large and is
 * only opened when the user clicks the "Connections" tab.
 */
@RestController
@RequestMapping("/api/console/network")
public class ConsoleNetworkController {

    private final NetworkMetricsService metricsService;
    private final ConsoleNetworkHistoryService historyService;
    private final NetworkConnectionsService connectionsService;

    public ConsoleNetworkController(NetworkMetricsService metricsService,
                                    ConsoleNetworkHistoryService historyService,
                                    NetworkConnectionsService connectionsService) {
        this.metricsService = metricsService;
        this.historyService = historyService;
        this.connectionsService = connectionsService;
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<MetricsEnvelope<NetworkMetricsDto>> latest() {
        NetworkMetricsService.CacheEntry e = metricsService.current();
        if (e.metrics() == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(new MetricsEnvelope<>(
                e.metrics(), e.reachable(), e.lastUpdateMillis(), e.lastError()));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<List<ConsoleNetworkSnapshotEntity>> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(historyService.between(from, to));
    }

    @GetMapping("/connections")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<NetworkConnectionsDto> connections() {
        return ResponseEntity.ok(connectionsService.snapshot());
    }
}
