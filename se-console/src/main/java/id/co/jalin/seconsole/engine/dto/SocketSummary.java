package id.co.jalin.seconsole.engine.dto;

public record SocketSummary(
        String bindingId,
        String socketId,
        String name,
        String type,
        Runtime runtime,
        Queue queue,
        Metrics metrics
) {

    public record Runtime(
            String state,
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
            Stat latency,
            Stat pressureTps,
            Stat throughputTps
    ) {}

    public record Stat(
            long avg,
            long min,
            long max,
            long p90,
            long p95,
            long p99
    ) {}
}
