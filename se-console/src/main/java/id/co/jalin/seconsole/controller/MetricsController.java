package id.co.jalin.seconsole.controller;

import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.service.SystemMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System metrics endpoints. Read-only; all authenticated roles can view
 * (viewer, operator, admin) per Foundation feature matrix 6.2.
 *
 * Results are service-side cached for 2 seconds so dashboard polling does not
 * hammer OSHI probes.
 */
@RestController
@RequestMapping("/api/system")
public class MetricsController {

    private final SystemMetricsService metricsService;

    public MetricsController(SystemMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'VIEWER')")
    public ResponseEntity<SystemMetricsDto> systemMetrics() {
        return ResponseEntity.ok(metricsService.snapshot());
    }
}
