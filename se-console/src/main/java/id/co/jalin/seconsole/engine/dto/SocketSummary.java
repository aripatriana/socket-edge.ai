package id.co.jalin.seconsole.engine.dto;

/**
 * Console-facing socket view, one per engine socket.
 *
 * <p>Shape mirrors the nested structure of {@code /socket/snapshot/channels}
 * so frontend consumers read {@code socket.metrics.latency.avgNs} instead of
 * flat prefixed field names. Full rewrite (Chat 3e-3); no backward-compat
 * with the pre-rewrite flat form.
 */
public record SocketSummary(
        String hashId,
        String socketId,
        String name,
        String type,            // CLIENT | SERVER
        Runtime runtime,
        Queue queue,
        Metrics metrics
) {

    public record Runtime(
            String state,       // DOWN | STANDBY | LISTEN | WAIT | ACTIVE | ERROR
            String localHost,
            String remoteHost,
            int activeChannels,
            long startTime,
            long lastConnect,
            long lastDisconnect
    ) {}

    public record Queue(
            long msgIn,
            long msgOut,
            long depth,
            long errCount,
            long lastErr,
            long lastMsg
    ) {}

    public record Metrics(
            Latency latency,
            Tps pressureTps,
            Tps throughputTps
    ) {}

    public record Latency(
            long avgNs,
            long minNs,
            long maxNs,
            long p90Ns,
            long p95Ns
    ) {}

    /** Per-socket TPS distribution — {@code avg} replaces spec's {@code current}. */
    public record Tps(
            long avg,
            long min,
            long max,
            long p90,
            long p95
    ) {}
}
