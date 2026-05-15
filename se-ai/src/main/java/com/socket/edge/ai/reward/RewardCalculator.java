package com.socket.edge.ai.reward;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes a channel-level reward in [-1, +1] from two consecutive ChannelStates.
 *
 * Reward components:
 *   latency_reward    = prev.avgLatencyP95   - curr.avgLatencyP95   (lower latency = positive)
 *   throughput_reward = curr.avgThroughputAvg - prev.avgThroughputAvg (higher throughput = positive)
 *   pressure_reward   = prev.avgPressureAvg  - curr.avgPressureAvg  (lower pressure = positive)
 *
 * Each delta is normalized by the running maximum absolute delta seen for that
 * metric, making the algorithm environment-agnostic (2ms or 100ms baseline).
 *
 * Final reward = 0.5·latency + 0.3·throughput + 0.2·pressure, clamped to [-1, +1].
 */
public class RewardCalculator {

    private static final double EPS = 1e-10;

    // running max absolute delta per channel per metric
    private final Map<String, double[]> runningMaxDelta = new ConcurrentHashMap<>();

    /**
     * Computes the reward for the given channel transition.
     *
     * @param prev  snapshot from the previous interval
     * @param curr  snapshot from the current interval
     * @return reward in [-1.0, +1.0]
     */
    public double compute(ChannelState prev, ChannelState curr) {
        double latencyDelta    = prev.avgLatencyP95()   - curr.avgLatencyP95();
        double throughputDelta = curr.avgThroughputAvg() - prev.avgThroughputAvg();
        double pressureDelta   = prev.avgPressureAvg()  - curr.avgPressureAvg();

        double[] maxDelta = runningMaxDelta.computeIfAbsent(
                prev.channelName(), k -> new double[]{EPS, EPS, EPS});

        maxDelta[0] = Math.max(maxDelta[0], Math.abs(latencyDelta));
        maxDelta[1] = Math.max(maxDelta[1], Math.abs(throughputDelta));
        maxDelta[2] = Math.max(maxDelta[2], Math.abs(pressureDelta));

        double normLatency    = latencyDelta    / maxDelta[0];
        double normThroughput = throughputDelta / maxDelta[1];
        double normPressure   = pressureDelta   / maxDelta[2];

        double reward = 0.5 * normLatency + 0.3 * normThroughput + 0.2 * normPressure;
        return Math.max(-1.0, Math.min(1.0, reward));
    }
}
