package id.co.jalin.seconsole.engine;

import id.co.jalin.seconsole.engine.dto.ChannelSummary;
import id.co.jalin.seconsole.engine.dto.SocketActionResponse;
import id.co.jalin.seconsole.engine.dto.SocketSummary;
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

import java.util.Map;

/**
 * Control endpoints for the Connections tab.
 *
 * <p>Two levels of granularity:
 * <ul>
 *   <li><strong>Channel-level</strong>: POST /api/channels/{name}/start (etc.)
 *       — forwards to engine {@code POST /socket/start?name={name}}, which
 *       batches over every socket in that channel.</li>
 *   <li><strong>Socket-level</strong>: POST /api/channels/{name}/sockets/{bindingId}/start
 *       — forwards to {@code POST /socket/start?id={bindingId}}.</li>
 * </ul>
 *
 * <p>The {@code {name}} in the socket-level URL is validated against the
 * cached snapshot to make sure the bindingId actually belongs to that channel
 * (prevents hash-collision confusion and makes audit trails self-describing).
 *
 * <p>Every successful or failed action writes one row to {@code audit_entry}
 * via {@link AuditService}.
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelControlController {

    private static final Logger log = LoggerFactory.getLogger(ChannelControlController.class);

    private final EngineClient engineClient;
    private final EngineChannelSnapshotService snapshotService;
    private final AuditService auditService;

    public ChannelControlController(EngineClient engineClient,
                                    EngineChannelSnapshotService snapshotService,
                                    AuditService auditService) {
        this.engineClient = engineClient;
        this.snapshotService = snapshotService;
        this.auditService = auditService;
    }

    // =========================================================================
    // Channel-level actions — loops all sockets under the name
    // =========================================================================

    @PostMapping("/{name}/start")
    public ResponseEntity<?> startChannel(@PathVariable String name) {
        return execute("start", "CHANNEL", "name", name, name, null);
    }

    @PostMapping("/{name}/stop")
    public ResponseEntity<?> stopChannel(@PathVariable String name) {
        return execute("stop", "CHANNEL", "name", name, name, null);
    }

    @PostMapping("/{name}/restart")
    public ResponseEntity<?> restartChannel(@PathVariable String name) {
        return execute("restart", "CHANNEL", "name", name, name, null);
    }

    // =========================================================================
    // Socket-level actions — single bindingId
    // =========================================================================

    @PostMapping("/{name}/sockets/{bindingId}/start")
    public ResponseEntity<?> startSocket(@PathVariable String name, @PathVariable String bindingId) {
        return execute("start", "SOCKET", "id", bindingId, bindingId, name);
    }

    @PostMapping("/{name}/sockets/{bindingId}/stop")
    public ResponseEntity<?> stopSocket(@PathVariable String name, @PathVariable String bindingId) {
        return execute("stop", "SOCKET", "id", bindingId, bindingId, name);
    }

    @PostMapping("/{name}/sockets/{bindingId}/restart")
    public ResponseEntity<?> restartSocket(@PathVariable String name, @PathVariable String bindingId) {
        return execute("restart", "SOCKET", "id", bindingId, bindingId, name);
    }

    // =========================================================================
    // Shared execution path
    // =========================================================================

    private ResponseEntity<?> execute(
            String action,
            String scope,
            String paramName,
            String paramValue,
            String target,
            String channelNameForValidation) {

        // Validate channel exists + bindingId belongs to it (for SOCKET scope).
        ChannelSummary channel = snapshotService.findChannel(
                channelNameForValidation != null ? channelNameForValidation : target);
        if (channelNameForValidation != null) {
            if (channel == null) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "channel_not_found",
                        "name", channelNameForValidation));
            }
            if (!socketBelongsToChannel(channel, target)) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "socket_not_in_channel",
                        "channel", channelNameForValidation,
                        "bindingId", target));
            }
        } else {
            // Channel-level action — just check the channel exists.
            if (channel == null) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "channel_not_found",
                        "name", target));
            }
        }

        long started = System.currentTimeMillis();
        String engineMessage = null;
        boolean success = false;
        String failMessage = null;

        try {
            engineMessage = engineClient.postSocketAction(action, paramName, paramValue);
            success = true;
        } catch (EngineClient.EngineClientException ex) {
            failMessage = ex.getMessage();
            log.warn("Engine rejected {}/{} on {} {}: {}", action, scope, paramName, paramValue, failMessage);
        }

        long durationMs = System.currentTimeMillis() - started;

        // Audit — log regardless of success so operators can see failed attempts too.
        try {
            String actor = currentUsername();
            String auditAction = action.toUpperCase() + "_" + scope;     // e.g. "STOP_SOCKET"
            String targetType = scope.toLowerCase();                     // "socket" | "channel"
            String targetId = scope.equals("SOCKET")
                    ? channelNameForValidation + "/" + target
                    : target;
            String detailsJson = success
                    ? "{\"engineMessage\":" + jsonString(engineMessage) + ",\"durationMs\":" + durationMs + "}"
                    : "{\"failMessage\":" + jsonString(failMessage) + ",\"durationMs\":" + durationMs + "}";
            if (success) {
                auditService.logSuccess(null, actor, auditAction, targetType, targetId,
                        detailsJson, null, null);
            } else {
                auditService.logFailure(null, actor, auditAction, targetType, targetId,
                        detailsJson, null, null);
            }
        } catch (Exception auditEx) {
            // Never let audit failure mask the action result.
            log.warn("Audit write failed for {} {}: {}", action, target, auditEx.getMessage());
        }

        SocketActionResponse body = new SocketActionResponse(
                success,
                action,
                scope,
                target,
                durationMs,
                success ? (engineMessage == null || engineMessage.isBlank() ? "OK" : engineMessage) : failMessage
        );

        return success
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(502).body(body);
    }

    private static boolean socketBelongsToChannel(ChannelSummary c, String bindingId) {
        for (SocketSummary s : c.servers()) if (bindingId.equals(s.bindingId())) return true;
        for (SocketSummary s : c.clients()) if (bindingId.equals(s.bindingId())) return true;
        return false;
    }

    /** Minimal JSON string literal — escapes quotes and backslashes. Null → "null". */
    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 4).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else if (ch == '\t') sb.append("\\t");
            else if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
            else sb.append(ch);
        }
        return sb.append('"').toString();
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return "unknown";
        return auth.getName();
    }
}
