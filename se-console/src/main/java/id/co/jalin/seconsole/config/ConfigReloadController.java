package id.co.jalin.seconsole.config;

import com.socket.edge.grpc.ControlResponse;
import id.co.jalin.seconsole.grpc.CoreGrpcClient;
import id.co.jalin.seconsole.service.AuditService;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reload endpoint — triggers an engine config reload via gRPC ReloadConfig.
 *
 * <p>From the UI's perspective the action is per-file
 * ({@code POST /api/config/reload/{fileName}}) but the engine reload
 * itself is not parameterized — it re-reads all config files. We still
 * accept and audit the file name so the audit trail captures operator
 * intent.
 *
 * <p>Failure modes:
 * <ul>
 *   <li>gRPC transport error → 502 with the error.</li>
 *   <li>Engine returns success=false → 502 with the engine message.</li>
 *   <li>Unexpected exception → 500.</li>
 * </ul>
 *
 * <p>Successful and failed reloads are both audited.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigReloadController {

    private static final Logger log = LoggerFactory.getLogger(ConfigReloadController.class);

    private final AuditService auditService;
    private final CoreGrpcClient grpcClient;

    public ConfigReloadController(AuditService auditService, CoreGrpcClient grpcClient) {
        this.auditService = auditService;
        this.grpcClient = grpcClient;
    }

    @PostMapping("/reload/{fileName}")
    public ResponseEntity<?> reload(@PathVariable String fileName) {
        long started = System.currentTimeMillis();
        String username = currentUsername();

        ControlResponse response;
        try {
            response = grpcClient.reloadConfig();
        } catch (StatusRuntimeException ex) {
            long durationMs = System.currentTimeMillis() - started;
            log.warn("Engine reload failed for {}: {}", fileName, ex.getStatus());
            try {
                auditService.logFailure(null, username, "RELOAD_CONFIG",
                        "config_file", fileName,
                        "{\"durationMs\":" + durationMs + ",\"error\":" + quote(ex.getStatus().toString()) + "}",
                        null, null);
            } catch (Exception auditEx) {
                log.warn("Audit write failed for reload failure {}: {}", fileName, auditEx.getMessage());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "error", "engine_reload_failed",
                    "message", ex.getStatus().toString(),
                    "durationMs", durationMs
            ));
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - started;
            log.error("Unexpected error during reload {}: {}", fileName, ex.getMessage(), ex);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "internal_error",
                    "message", ex.getMessage(),
                    "durationMs", durationMs
            ));
        }

        long durationMs = System.currentTimeMillis() - started;

        if (!response.getSuccess()) {
            String errorMsg = response.getMessage();
            log.warn("Engine rejected reload for {}: {}", fileName, errorMsg);
            try {
                auditService.logFailure(null, username, "RELOAD_CONFIG",
                        "config_file", fileName,
                        "{\"durationMs\":" + durationMs + ",\"error\":" + quote(errorMsg) + "}",
                        null, null);
            } catch (Exception auditEx) {
                log.warn("Audit write failed for reload failure {}: {}", fileName, auditEx.getMessage());
            }
            return ResponseEntity.status(502).body(Map.of(
                    "error", "engine_reload_failed",
                    "message", errorMsg,
                    "durationMs", durationMs
            ));
        }

        String engineMessage = response.getMessage();
        try {
            auditService.logSuccess(null, username, "RELOAD_CONFIG",
                    "config_file", fileName,
                    "{\"durationMs\":" + durationMs
                            + ",\"engineMessage\":" + quote(engineMessage) + "}",
                    null, null);
        } catch (Exception ex) {
            log.warn("Audit write failed for reload {}: {}", fileName, ex.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("fileName", fileName);
        body.put("durationMs", durationMs);
        body.put("engineReloaded", true);
        body.put("message", engineMessage == null || engineMessage.isEmpty()
                ? "Engine reloaded successfully."
                : engineMessage);
        return ResponseEntity.ok(body);
    }

    // --- internals ----------------------------------------------------------

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return "unknown";
        return auth.getName();
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}