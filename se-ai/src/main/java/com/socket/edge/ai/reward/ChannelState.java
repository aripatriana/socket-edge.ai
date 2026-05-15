package com.socket.edge.ai.reward;

import com.socket.edge.grpc.SocketSnapshot;

import java.util.List;

/**
 * Aggregated channel-level metrics snapshot for reward computation.
 *
 * Computed from the CLIENT sockets of one channel at a single point in time.
 * The reward compares the delta between two consecutive ChannelStates.
 *
 * @param channelName     logical channel identifier
 * @param avgLatencyP95   mean p95 latency (ns) across all live endpoints
 * @param avgThroughputAvg mean avg throughput (TPS) across all live endpoints
 * @param avgPressureAvg  mean avg pressure (TPS) across all live endpoints
 */
public record ChannelState(
        String channelName,
        double avgLatencyP95,
        double avgThroughputAvg,
        double avgPressureAvg
) {

    /** Builds a ChannelState from a list of CLIENT SocketSnapshots for one channel. */
    public static ChannelState from(String channelName, List<SocketSnapshot> endpoints) {
        if (endpoints.isEmpty()) {
            return new ChannelState(channelName, 0, 0, 0);
        }
        double latP95 = 0, thrAvg = 0, presAvg = 0;
        for (SocketSnapshot s : endpoints) {
            latP95  += s.getMetrics().getLatencyNs().getP95();
            thrAvg  += s.getMetrics().getThroughputTps().getAvg();
            presAvg += s.getMetrics().getPressureTps().getAvg();
        }
        int n = endpoints.size();
        return new ChannelState(channelName, latP95 / n, thrAvg / n, presAvg / n);
    }
}
