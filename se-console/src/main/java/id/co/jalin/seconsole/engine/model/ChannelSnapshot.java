package id.co.jalin.seconsole.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Engine-side channel snapshot, wire-compatible with
 * {@code GET /socket/snapshot/channels} per {@code spec-channel-snapshot.md}.
 *
 * <p><b>Deviations from the spec</b> (agreed with the operator):
 * <ul>
 *   <li>{@code pressureTps.current} / {@code throughputTps.current} →
 *       {@code pressureTps.avg} / {@code throughputTps.avg} for per-socket metrics.
 *       Aggregate {@code currentPressureTps} / {@code currentThroughputTps} also
 *       renamed to {@code avgPressureTps} / {@code avgThroughputTps}.</li>
 *   <li>{@code engineState} field is omitted — not used by the console.</li>
 *   <li>Socket {@code runtime.state} carries the six-state baseline
 *       ({@code DOWN, STANDBY, LISTEN, WAIT, ACTIVE, ERROR}) rather than the
 *       four-state ({@code UP, DOWN, STARTING, STOPPING}) named in the spec.
 *       WireMock and the real engine both emit the six-state form.</li>
 * </ul>
 *
 * <p>Unknown fields are ignored so the engine can add attributes without
 * breaking the console.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChannelSnapshot(
        String snapshotId,
        long capturedAt,
        long captureDurationMs,
        List<Socket> sockets,
        Aggregate aggregate
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Socket(
            String bindingId,
            String socketId,
            String name,
            String type,            // CLIENT | SERVER
            Runtime runtime,
            Queue queue,
            Metrics metrics
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Runtime(
            String state,           // DOWN | STANDBY | LISTEN | WAIT | ACTIVE | ERROR
            String localHost,
            String remoteHost,
            int activeChannels,
            long startTime,
            long lastConnect,
            long lastDisconnect
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Queue(
            long msgIn,
            long msgOut,
            long depth,
            long errCount,
            long lastErr,
            long lastMsg
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metrics(
            Latency latency,
            Tps pressureTps,
            Tps throughputTps
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Latency(
            long avgNs,
            long minNs,
            long maxNs,
            long p90Ns,
            long p95Ns
    ) {}

    /** TPS distribution — per-socket. Note: {@code avg} replaces {@code current}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tps(
            long avg,
            long min,
            long max,
            long p90,
            long p95
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aggregate(
            int socketCount,
            int socketsUp,
            int socketsDown,
            int totalActiveChannels,
            long totalMsgIn,
            long totalMsgOut,
            long totalQueueDepth,
            long totalErrCount,
            long avgPressureTps,
            long avgThroughputTps
    ) {}
}
