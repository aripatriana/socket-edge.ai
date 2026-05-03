package id.co.jalin.seconsole.engine.dto;

import java.util.List;

/**
 * Per-channel aggregated view for the channels list AND detail pages.
 *
 * Aggregate state rules:
 *   ANY socket in ERROR          → ERROR
 *   ALL sockets ACTIVE or LISTEN → ACTIVE
 *   ALL sockets DOWN or STANDBY  → DOWN
 *   otherwise                    → DEGRADED
 */
public record ChannelSummary(
        String name,
        String aggregateState,
        int socketsUp,
        int socketsTotal,

        Aggregate aggregate,

        Integer listenPort,
        String clientStrategy,

        List<SocketSummary> servers,
        List<SocketSummary> clients
) {

    public record Aggregate(
            Latency latency,
            PressureTps pressureTps,
            ThroughputTps throughputTps,

            long totalMsgIn,
            long totalMsgOut,
            long totalInFlight,
            long totalErrCnt,
            long maxLastErrMs
    ) {}

    /** Worst-of across sockets — max values are the actionable signal. */
    public record Latency(
            long maxAvgNs,
            long maxMaxNs,
            long maxP95Ns,
            long maxP99Ns
    ) {}

    public record PressureTps(
            long totalAvg,
            long maxAvg,
            long maxP95,
            long maxP99
    ) {}

    public record ThroughputTps(
            long totalAvg,
            long maxAvg,
            long maxP95,
            long maxP99
    ) {}
}
