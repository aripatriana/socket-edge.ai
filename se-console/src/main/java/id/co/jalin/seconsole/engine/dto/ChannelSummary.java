package id.co.jalin.seconsole.engine.dto;

import java.util.List;

/**
 * Per-channel aggregated view for the channels list AND detail pages.
 *
 * <p>Full rewrite (Chat 3e-3). Previously flat, now nested to mirror the
 * shape of individual {@link SocketSummary} records so the frontend reads
 * {@code channel.aggregate.throughputTps.totalAvg} in the same pattern as
 * {@code socket.metrics.throughputTps.avg}.
 *
 * <p>Aggregate state rules (unchanged from pre-rewrite):
 * <pre>
 *   ANY socket in ERROR           → ERROR
 *   ALL sockets ACTIVE or LISTEN  → ACTIVE
 *   ALL sockets DOWN or STANDBY   → DOWN
 *   otherwise                     → DEGRADED
 * </pre>
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

    /** Per-channel aggregates — sums and worsts across the channel's sockets. */
    public record Aggregate(
            Latency latency,
            PressureTps pressureTps,
            ThroughputTps throughputTps,

            // Lifetime counters — sum across sockets
            long totalMsgIn,
            long totalMsgOut,

            // Queue
            long totalInFlight,

            // Errors
            long totalErrCnt,
            long maxLastErrMs         // 0 if no socket in this channel ever errored
    ) {}

    /** Worst-of across sockets — we surface max values because that's the
     *  actionable signal for the operator (one hot socket matters). */
    public record Latency(
            long maxAvgNs,
            long maxMaxNs,
            long maxP95Ns
    ) {}

    /** Channel-level pressure distribution. */
    public record PressureTps(
            long totalAvg,            // sum of per-socket avg
            long maxAvg,              // max of per-socket avg
            long maxP95
    ) {}

    /** Channel-level throughput distribution. */
    public record ThroughputTps(
            long totalAvg,
            long maxAvg,
            long maxP95
    ) {}
}
