package id.co.jalin.seconsole.dto.response;

/**
 * Envelope returned by the {@code /api/console/&lt;domain&gt;/latest} endpoints.
 *
 * <p>Carries the DTO alongside probe reachability metadata — the UI uses
 * {@code reachable} + {@code lastUpdateMillis} to distinguish "just polled,
 * data is current" from "probe failed, what you see is stale". Mirrors the
 * envelope the engine services already expose via their cache entries.
 */
public record MetricsEnvelope<T>(
        T metrics,
        boolean reachable,
        long lastUpdateMillis,
        String lastError) {
}
