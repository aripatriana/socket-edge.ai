package id.co.jalin.seconsole.engine;

/**
 * Engine response envelope.
 *
 * <p>Every endpoint on the engine's {@code NettyHttpServer} wraps its payload with:
 * <pre>{ "status": "OK"|"FAILED", "result": T, "message": "..." }</pre>
 *
 * @param status  "OK" on success, "FAILED" otherwise
 * @param result  endpoint-specific payload (nullable on failure)
 * @param message optional human-readable message (usually populated on failure)
 */
public record EngineResponse<T>(String status, T result, String message) {

    public boolean ok() {
        return "OK".equalsIgnoreCase(status);
    }

    public String messageOrDefault(String fallback) {
        return (message == null || message.isBlank()) ? fallback : message;
    }
}
