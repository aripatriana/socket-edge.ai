package com.socket.edge.ai.sim;

import com.socket.edge.grpc.*;

/**
 * Test helper — builds MetricsBundle proto objects with configurable endpoint metrics.
 *
 * Usage:
 *   MetricsBundle bundle = MetricBundleFactory.bundle(
 *       MetricBundleFactory.client("ep1", "fello", 1_000, 100, 10, "ACTIVE"),
 *       MetricBundleFactory.client("ep2", "fello", 5_000, 100, 10, "ACTIVE")
 *   );
 */
public final class MetricBundleFactory {

    private MetricBundleFactory() {}

    /**
     * Builds a single CLIENT SocketSnapshot.
     *
     * @param bindingId    unique endpoint identifier (maps to binding_id)
     * @param channelName  channel this endpoint belongs to (maps to name field)
     * @param latencyAvgNs average latency in nanoseconds
     * @param throughputAvg average throughput TPS
     * @param pressureAvg  average pressure TPS
     * @param state        runtime state: ACTIVE | DOWN | ERROR | WAIT | STANDBY
     */
    public static SocketSnapshot client(String bindingId,
                                        String channelName,
                                        long latencyAvgNs,
                                        long throughputAvg,
                                        long pressureAvg,
                                        String state) {
        // derive plausible min/max/p90/p95 from avg
        long latMin  = (long) (latencyAvgNs * 0.6);
        long latMax  = (long) (latencyAvgNs * 2.0);
        long latP90  = (long) (latencyAvgNs * 1.5);
        long latP95  = (long) (latencyAvgNs * 1.7);

        long thrMin  = (long) (throughputAvg * 0.7);
        long thrMax  = (long) (throughputAvg * 1.3);
        long thrP90  = (long) (throughputAvg * 1.1);
        long thrP95  = (long) (throughputAvg * 1.2);

        long presMin = (long) (pressureAvg * 0.7);
        long presMax = (long) (pressureAvg * 1.3);
        long presP90 = (long) (pressureAvg * 1.1);
        long presP95 = (long) (pressureAvg * 1.2);

        return SocketSnapshot.newBuilder()
                .setBindingId(bindingId)
                .setSocketId("socket-" + bindingId)
                .setName(channelName)
                .setType("CLIENT")
                .setRuntime(SocketRuntime.newBuilder().setState(state).build())
                .setMetrics(SocketMetrics.newBuilder()
                        .setLatencyNs(stat(latencyAvgNs, latMin, latMax, latP90, latP95))
                        .setThroughputTps(stat(throughputAvg, thrMin, thrMax, thrP90, thrP95))
                        .setPressureTps(stat(pressureAvg, presMin, presMax, presP90, presP95))
                        .build())
                .build();
    }

    /**
     * Builds a SERVER SocketSnapshot — included in bundles to verify se-ai filters correctly.
     */
    public static SocketSnapshot server(String bindingId, String channelName) {
        return SocketSnapshot.newBuilder()
                .setBindingId(bindingId)
                .setSocketId("server-" + bindingId)
                .setName(channelName)
                .setType("SERVER")
                .setRuntime(SocketRuntime.newBuilder().setState("LISTEN").build())
                .setMetrics(SocketMetrics.newBuilder()
                        .setLatencyNs(stat(0, 0, 0, 0, 0))
                        .setThroughputTps(stat(0, 0, 0, 0, 0))
                        .setPressureTps(stat(0, 0, 0, 0, 0))
                        .build())
                .build();
    }

    /**
     * Wraps SocketSnapshots into a MetricsBundle with a ChannelSnapshot.
     */
    public static MetricsBundle bundle(SocketSnapshot... sockets) {
        ChannelSnapshot.Builder ch = ChannelSnapshot.newBuilder();
        for (SocketSnapshot s : sockets) ch.addSockets(s);
        return MetricsBundle.newBuilder().setChannel(ch.build()).build();
    }

    private static StatSummary stat(long avg, long min, long max, long p90, long p95) {
        return StatSummary.newBuilder()
                .setAvg(avg).setMin(min).setMax(max).setP90(p90).setP95(p95)
                .build();
    }
}
