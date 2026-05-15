package com.socket.edge.ai.feature;

import com.socket.edge.grpc.SocketMetrics;
import com.socket.edge.grpc.SocketSnapshot;
import com.socket.edge.grpc.StatSummary;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a normalized feature vector from a sorted list of CLIENT SocketSnapshots
 * belonging to one channel.
 *
 * Feature layout per endpoint i (0-indexed), 15 features each:
 *   [i*15 +  0] latency_ns.avg
 *   [i*15 +  1] latency_ns.min
 *   [i*15 +  2] latency_ns.max
 *   [i*15 +  3] latency_ns.p90
 *   [i*15 +  4] latency_ns.p95
 *   [i*15 +  5] throughput_tps.avg
 *   [i*15 +  6] throughput_tps.min
 *   [i*15 +  7] throughput_tps.max
 *   [i*15 +  8] throughput_tps.p90
 *   [i*15 +  9] throughput_tps.p95
 *   [i*15 + 10] pressure_tps.avg
 *   [i*15 + 11] pressure_tps.min
 *   [i*15 + 12] pressure_tps.max
 *   [i*15 + 13] pressure_tps.p90
 *   [i*15 + 14] pressure_tps.p95
 *
 * Normalization: running min-max per feature position, per channel.
 * Normalizers reset when endpoint count changes.
 */
public class FeatureBuilder {

    public static final int FEATURES_PER_ENDPOINT = 15;

    // per-channel normalizer, keyed by channel name
    private final Map<String, RunningNormalizer> normalizers = new ConcurrentHashMap<>();
    // tracks last known endpoint count per channel to detect topology changes
    private final Map<String, Integer> endpointCounts = new ConcurrentHashMap<>();

    /**
     * Builds a normalized feature vector for the given channel.
     *
     * @param channelName  the logical channel name (e.g. "fello")
     * @param endpoints    CLIENT SocketSnapshots, sorted by binding_id for stability
     * @return normalized double[] of length endpoints.size() * 15
     */
    public double[] build(String channelName, List<SocketSnapshot> endpoints) {
        int n = endpoints.size();
        int featureSize = n * FEATURES_PER_ENDPOINT;

        RunningNormalizer normalizer = normalizers.compute(channelName, (k, existing) -> {
            Integer prevCount = endpointCounts.get(k);
            if (existing == null || prevCount == null || prevCount != n) {
                // topology changed or first time — reset normalizer
                return new RunningNormalizer(featureSize);
            }
            return existing;
        });
        endpointCounts.put(channelName, n);

        double[] raw = extractRaw(endpoints);
        return normalizer.updateAndNormalize(raw);
    }

    private double[] extractRaw(List<SocketSnapshot> endpoints) {
        double[] raw = new double[endpoints.size() * FEATURES_PER_ENDPOINT];
        for (int i = 0; i < endpoints.size(); i++) {
            SocketMetrics m = endpoints.get(i).getMetrics();
            int base = i * FEATURES_PER_ENDPOINT;
            raw[base + 0]  = statAvg(m.getLatencyNs());
            raw[base + 1]  = statMin(m.getLatencyNs());
            raw[base + 2]  = statMax(m.getLatencyNs());
            raw[base + 3]  = m.getLatencyNs().getP90();
            raw[base + 4]  = m.getLatencyNs().getP95();
            raw[base + 5]  = statAvg(m.getThroughputTps());
            raw[base + 6]  = statMin(m.getThroughputTps());
            raw[base + 7]  = statMax(m.getThroughputTps());
            raw[base + 8]  = m.getThroughputTps().getP90();
            raw[base + 9]  = m.getThroughputTps().getP95();
            raw[base + 10] = statAvg(m.getPressureTps());
            raw[base + 11] = statMin(m.getPressureTps());
            raw[base + 12] = statMax(m.getPressureTps());
            raw[base + 13] = m.getPressureTps().getP90();
            raw[base + 14] = m.getPressureTps().getP95();
        }
        return raw;
    }

    private static double statAvg(StatSummary s) { return s.getAvg(); }
    private static double statMin(StatSummary s) { return s.getMin(); }
    private static double statMax(StatSummary s) { return s.getMax(); }
}
