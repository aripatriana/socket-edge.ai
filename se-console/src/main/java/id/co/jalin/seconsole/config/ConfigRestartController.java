package id.co.jalin.seconsole.config;

import id.co.jalin.seconsole.service.AuditService;
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
 * Engine restart endpoint — full stop + start, distinct from the graceful
 * {@link ConfigReloadController reload}.
 *
 * <p><b>Current state: stub.</b> The real restart mechanism is still being
 * decided — candidates include {@code jsocket.sh restart}, a JMX operation,
 * or a systemd unit. Rather than block the UI work on that decision, this
 * controller accepts the request, records an audit entry, and returns
 * {@code engineRestarted=false} with a message making the no-op explicit.
 *
 * <p>The shape matches {@link ConfigReloadController} so the frontend can
 * reuse its banner / error-handling patterns without special-casing this
 * endpoint. When real restart lands, the {@link #executeRestart} method is
 * the single point to change — no controller, DTO, or FE wiring churn.
 *
 * <p><b>Why a separate endpoint rather than a flag on reload?</b> Restart
 * has very different operational semantics: all TCP connections drop,
 * inflight messages are lost, and the engine is unavailable for seconds.
 * Putting it under its own URL and HTTP verb forces auditors, proxies, and
 * future rate-limiters to treat it as a distinct, heavier operation.
 *
 * <p>Audit: every call is logged with action {@code RESTART_ENGINE}
 * regardless of which file triggered it — restart is global, the file name
 * only captures operator intent.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigRestartController {

    private static final Logger log = LoggerFactory.getLogger(ConfigRestartController.class);

    /**
     * Message shown to the operator until real restart wiring lands. Keeping
     * this in one place so it reads the same in logs, audit, and the UI
     * banner, and so one rename when the feature ships updates all three.
     */
    private static final String STUB_MESSAGE =
            "Restart requested but not yet wired — engine was not actually restarted. "
                    + "Restart implementation pending (see ConfigRestartController javadoc).";

    private final AuditService auditService;

    public ConfigRestartController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/restart/{fileName}")
    public ResponseEntity<?> restart(@PathVariable String fileName) {
        long started = System.currentTimeMillis();
        String username = currentUsername();

        RestartOutcome outcome;
        try {
            outcome = executeRestart(fileName);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - started;
            log.error("Unexpected error during restart {}: {}", fileName, ex.getMessage(), ex);

            try {
                auditService.logFailure(null, username, "RESTART_ENGINE",
                        "engine", fileName,
                        "{\"durationMs\":" + durationMs + ",\"error\":" + quote(ex.getMessage()) + "}",
                        null, null);
            } catch (Exception auditEx) {
                log.warn("Audit write failed for restart failure {}: {}", fileName, auditEx.getMessage());
            }

            return ResponseEntity.status(500).body(Map.of(
                    "error", "internal_error",
                    "message", ex.getMessage(),
                    "durationMs", durationMs
            ));
        }

        long durationMs = System.currentTimeMillis() - started;

        // Audit regardless of whether the stub did any real work — operator
        // intent is what matters for the audit trail, and the audit row
        // captures `engineRestarted` so we can later distinguish stub calls
        // from real ones in the same table.
        try {
            auditService.logSuccess(null, username, "RESTART_ENGINE",
                    "engine", fileName,
                    "{\"durationMs\":" + durationMs
                            + ",\"engineRestarted\":" + outcome.engineRestarted()
                            + ",\"message\":" + quote(outcome.message()) + "}",
                    null, null);
        } catch (Exception ex) {
            log.warn("Audit write failed for restart {}: {}", fileName, ex.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("fileName", fileName);
        body.put("durationMs", durationMs);
        body.put("engineRestarted", outcome.engineRestarted());
        body.put("message", outcome.message());
        return ResponseEntity.ok(body);
    }

    /**
     * Single point where real restart will be wired. Keeping this as a
     * method — rather than inline in the handler — makes the eventual
     * diff trivial: one method body, no touching of request/response plumbing.
     */
    private RestartOutcome executeRestart(String fileName) {
        log.info("Restart requested for {} — stub, not actually restarting engine", fileName);
        return new RestartOutcome(false, STUB_MESSAGE);
    }

    private record RestartOutcome(boolean engineRestarted, String message) {}

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
