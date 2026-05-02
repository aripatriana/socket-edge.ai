package id.co.jalin.seconsole.config;

import id.co.jalin.seconsole.domain.ConfigVersion;
import id.co.jalin.seconsole.repository.ConfigVersionRepository;
import id.co.jalin.seconsole.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * History + diff + rollback for config files.
 *
 * <p>Endpoints (all under /api/config):
 * <ul>
 *   <li>GET  /history/{fileName}            — list versions, newest first</li>
 *   <li>GET  /history/{fileName}/{v}        — get one version's content</li>
 *   <li>GET  /diff/{fileName}/{v1}/{v2}     — line-level diff of v1 against v2</li>
 *   <li>POST /rollback/{fileName}/{v}       — apply {v}'s content as a new version</li>
 * </ul>
 *
 * <p>Rollback is modeled as "create a new version whose content equals v{target}"
 * rather than "delete newer versions" — append-only history matches the schema
 * comment in V1__initial_schema.sql.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigHistoryController {

    private static final Logger log = LoggerFactory.getLogger(ConfigHistoryController.class);

    private final ConfigVersionRepository repository;
    private final AuditService auditService;
    private final ConfigService configService;

    public ConfigHistoryController(ConfigVersionRepository repository,
                                   AuditService auditService,
                                   ConfigService configService) {
        this.repository = repository;
        this.auditService = auditService;
        this.configService = configService;
    }

    // ------------------------------------------------------------------------
    // GET /history/{fileName}
    // ------------------------------------------------------------------------

    @GetMapping("/history/{fileName}")
    public ResponseEntity<?> list(@PathVariable String fileName) {
        List<ConfigVersion> versions = repository.findByFileNameOrderByVersionDesc(fileName);
        List<Map<String, Object>> items = versions.stream().map(ConfigHistoryController::summary).toList();

        Integer currentVersion = versions.isEmpty() ? null : versions.get(0).getVersion();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", fileName);
        body.put("currentVersion", currentVersion);
        body.put("versions", items);
        return ResponseEntity.ok(body);
    }

    // ------------------------------------------------------------------------
    // GET /history/{fileName}/{v}
    // ------------------------------------------------------------------------

    @GetMapping("/history/{fileName}/{v}")
    public ResponseEntity<?> getVersion(@PathVariable String fileName, @PathVariable Integer v) {
        Optional<ConfigVersion> found = repository.findByFileNameAndVersion(fileName, v);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "version_not_found",
                    "fileName", fileName,
                    "version", v));
        }
        ConfigVersion cv = found.get();
        Map<String, Object> body = new LinkedHashMap<>(summary(cv));
        body.put("content", cv.getContent());
        return ResponseEntity.ok(body);
    }

    // ------------------------------------------------------------------------
    // GET /diff/{fileName}/{v1}/{v2}
    // ------------------------------------------------------------------------

    @GetMapping("/diff/{fileName}/{v1}/{v2}")
    public ResponseEntity<?> diff(
            @PathVariable String fileName,
            @PathVariable Integer v1,
            @PathVariable Integer v2) {
        Optional<ConfigVersion> a = repository.findByFileNameAndVersion(fileName, v1);
        Optional<ConfigVersion> b = repository.findByFileNameAndVersion(fileName, v2);
        if (a.isEmpty() || b.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "version_not_found",
                    "fileName", fileName,
                    "v1Found", a.isPresent(),
                    "v2Found", b.isPresent()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", fileName);
        body.put("v1", summary(a.get()));
        body.put("v2", summary(b.get()));
        body.put("v1Content", a.get().getContent());
        body.put("v2Content", b.get().getContent());
        // Line counts up front — the client shows "+N / -M" without having to
        // split the strings again after doing its own diff.
        body.put("v1Lines", countLines(a.get().getContent()));
        body.put("v2Lines", countLines(b.get().getContent()));
        return ResponseEntity.ok(body);
    }

    // ------------------------------------------------------------------------
    // POST /rollback/{fileName}/{v}
    // ------------------------------------------------------------------------

    @PostMapping("/rollback/{fileName}/{v}")
    public ResponseEntity<?> rollback(@PathVariable String fileName, @PathVariable Integer v) {
        Optional<ConfigVersion> target = repository.findByFileNameAndVersion(fileName, v);
        if (target.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "version_not_found",
                    "fileName", fileName,
                    "version", v));
        }

        ConfigVersion src = target.get();
        long started = System.currentTimeMillis();

        // Real rollback: write source version's content to disk + append a new
        // version row via ConfigService.apply, then update that row's
        // applyResult to "rolled_back" + rolledBackFromVersion so timeline
        // badges render correctly.
        try {
            int currentVersion = repository.findMaxVersion(fileName).orElse(0);
            ConfigService.ApplyResult applyResult = configService.apply(
                    fileName, src.getContent(), currentVersion);

            if (applyResult.validationFailure() != null) {
                // Shouldn't happen — we're restoring content that was valid when first applied.
                return ResponseEntity.status(500).body(Map.of(
                        "error", "rollback_validation_failed",
                        "message", "Source version content failed re-validation"));
            }
            if (applyResult.currentVersionOnConflict() != null) {
                // Extremely rare: another apply raced this rollback. Frontend retries.
                return ResponseEntity.status(409).body(Map.of(
                        "error", "version_conflict",
                        "currentVersion", applyResult.currentVersionOnConflict()));
            }

            Integer newVersion = applyResult.newVersion();

            // Mark the freshly-created row as a rollback so the timeline badge renders.
            repository.findByFileNameAndVersion(fileName, newVersion).ifPresent(fresh -> {
                fresh.setApplyResult("rolled_back");
                fresh.setRolledBackFromVersion(v);
                fresh.setDescription("Rolled back to v" + v);
                repository.save(fresh);
            });

            try {
                auditService.logSuccess(null, currentUsername(), "ROLLBACK_CONFIG",
                        "config_version", fileName + "/v" + v,
                        "{\"newVersion\":" + newVersion + ",\"rolledBackFrom\":" + v
                                + ",\"totalMs\":" + (System.currentTimeMillis() - started) + "}",
                        null, null);
            } catch (Exception ex) {
                log.warn("Audit write failed for rollback {}/{}: {}", fileName, v, ex.getMessage());
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("fileName", fileName);
            body.put("newVersion", newVersion);
            body.put("rolledBackFrom", v);
            body.put("applyDurationMs", (int) (System.currentTimeMillis() - started));
            return ResponseEntity.ok(body);
        } catch (IOException ioe) {
            log.warn("Disk write failed for rollback {}/{}: {}", fileName, v, ioe.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", "io_error",
                    "message", ioe.getMessage()));
        }
    }

    // ------------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------------

    private static Map<String, Object> summary(ConfigVersion cv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", cv.getVersion());
        m.put("fileName", cv.getFileName());
        m.put("contentSha", cv.getContentSha());
        m.put("author", cv.getAuthorUsername());
        m.put("description", cv.getDescription());
        m.put("appliedAt", cv.getAppliedAt() == null ? null : cv.getAppliedAt().toEpochMilli());
        m.put("applyDurationMs", cv.getApplyDurationMs());
        m.put("applyResult", cv.getApplyResult());
        m.put("rolledBackFromVersion", cv.getRolledBackFromVersion());
        m.put("milestone", cv.isMilestone());
        m.put("size", cv.getContent() == null ? 0 : cv.getContent().length());
        return m;
    }

    private static int countLines(String content) {
        if (content == null || content.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') count++;
        }
        return count;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return "unknown";
        return auth.getName();
    }
}
