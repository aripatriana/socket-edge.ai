package id.co.jalin.seconsole.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration menu — list / read / validate / apply.
 *
 * <p>History + diff + rollback live in a sibling controller
 * ({@link ConfigHistoryController}) to keep each file focused. Engine
 * reload is in a third sibling ({@link ConfigReloadController}).
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final ConfigFileRegistry registry;
    private final ConfigService configService;
    private final ConfigValidator validator;

    public ConfigController(ConfigFileRegistry registry,
                            ConfigService configService,
                            ConfigValidator validator) {
        this.registry = registry;
        this.configService = configService;
        this.validator = validator;
    }

    // ------------------------------------------------------------------------
    // GET /files
    // ------------------------------------------------------------------------

    @GetMapping("/files")
    public ResponseEntity<?> listFiles() {
        List<ConfigFileRegistry.FileMetadata> entries = registry.list();

        List<Map<String, Object>> files = new ArrayList<>(entries.size());
        for (ConfigFileRegistry.FileMetadata f : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.name());
            m.put("path", f.absolutePath());
            m.put("section", f.section());
            m.put("size", f.size());
            m.put("currentVersion", 0); // populated from DB after apply — 0 until then
            m.put("lastModified", f.lastModified());
            m.put("lastModifiedBy", null);
            m.put("exists", f.exists());
            files.add(m);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("files", files);
        return ResponseEntity.ok(body);
    }

    // ------------------------------------------------------------------------
    // GET /files/{fileName}
    // ------------------------------------------------------------------------

    @GetMapping("/files/{fileName}")
    public ResponseEntity<?> readFile(@PathVariable String fileName) {
        try {
            ConfigService.ReadResult r = configService.read(fileName);
            if (r.content() == null) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "file_not_found",
                        "name", fileName,
                        "absolutePath", r.absolutePath()
                ));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", fileName);
            body.put("path", r.absolutePath());
            body.put("content", r.content());
            body.put("currentVersion", r.currentVersion() == null ? 0 : r.currentVersion());
            body.put("lastModified", r.lastModified());
            body.put("lastModifiedBy", r.lastModifiedBy());
            body.put("size", r.size());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", ex.getMessage()));
        } catch (IOException ex) {
            log.warn("Failed to read {}: {}", fileName, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", "io_error",
                    "message", ex.getMessage()));
        }
    }

    // ------------------------------------------------------------------------
    // POST /validate
    // ------------------------------------------------------------------------

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody ValidateRequest req) {
        long started = System.currentTimeMillis();
        if (req == null || req.name() == null || req.content() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", "name and content are required"));
        }
        if (registry.resolve(req.name()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", "File not in whitelist: " + req.name()));
        }

        ConfigValidator.Result result = validator.validate(req.name(), req.content());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", result.valid());
        body.put("errors", result.errors());
        body.put("warnings", result.warnings());
        body.put("durationMs", System.currentTimeMillis() - started);
        return ResponseEntity.ok(body);
    }

    // ------------------------------------------------------------------------
    // PUT /files/{fileName}
    // ------------------------------------------------------------------------

    @PutMapping("/files/{fileName}")
    public ResponseEntity<?> apply(@PathVariable String fileName, @RequestBody ApplyRequest req) {
        if (req == null || req.content() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", "content is required"));
        }

        try {
            ConfigService.ApplyResult result = configService.apply(
                    fileName, req.content(), req.expectedVersion());

            if (result.validationFailure() != null) {
                var v = result.validationFailure();
                return ResponseEntity.badRequest().body(Map.of(
                        "valid", false,
                        "errors", v.errors(),
                        "warnings", v.warnings()
                ));
            }

            if (result.currentVersionOnConflict() != null) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "version_conflict",
                        "currentVersion", result.currentVersionOnConflict(),
                        "expectedVersion", result.expectedVersionOnConflict()
                ));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("newVersion", result.newVersion());
            body.put("engineReloadMs", 0);    // reload is a separate endpoint now
            body.put("totalMs", result.totalMs());
            body.put("message", "Applied as v" + result.newVersion()
                    + ". Use the Reload action to propagate changes to the engine.");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", ex.getMessage()));
        } catch (IOException ex) {
            log.warn("Disk write failed for {}: {}", fileName, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", "io_error",
                    "message", ex.getMessage()));
        }
    }

    // ------------------------------------------------------------------------
    // Request DTOs
    // ------------------------------------------------------------------------

    public record ValidateRequest(String name, String content) {}

    public record ApplyRequest(String content, Integer expectedVersion) {}
}
