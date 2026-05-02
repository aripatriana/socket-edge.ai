package id.co.jalin.seconsole.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Application health probe.
 *
 * <p>This is a lightweight endpoint intended for the React landing
 * page to confirm the backend is reachable. It is <em>not</em> a
 * replacement for Spring Boot Actuator's {@code /actuator/health},
 * which does deeper liveness checks (DB, disk, etc.). Use this one
 * for UX confirmation; use Actuator for systemd liveness probes.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Value("${spring.application.name:se-console}")
    private String appName;

    @Value("${info.app.version:0.1.0-SNAPSHOT}")
    private String version;

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
            "status",    "UP",
            "service",   appName,
            "version",   version,
            "timestamp", Instant.now().toString()
        );
    }
}
