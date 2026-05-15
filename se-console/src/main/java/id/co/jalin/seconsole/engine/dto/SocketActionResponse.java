package id.co.jalin.seconsole.engine.dto;

/**
 * Result of a socket control action (start / stop / restart).
 *
 * <p>{@code target} is the bindingId or channel name depending on the action's
 * scope. {@code scope} disambiguates ("SOCKET" for bindingId, "CHANNEL" for
 * name-based batch actions).
 *
 * <p>{@code durationMs} measures end-to-end — SE-Console → engine → SE-Console
 * — useful for operator UX ("action took 320ms").
 */
public record SocketActionResponse(
        boolean success,
        String action,          // "start" | "stop" | "restart"
        String scope,           // "SOCKET" | "CHANNEL"
        String target,          // bindingId or channel name
        long durationMs,
        String message          // engine message or local error
) {}
