package com.socket.edge.ai.feature;

import java.util.Arrays;

/**
 * Online running min-max normalizer for a fixed-size feature vector.
 *
 * Each call to {@link #updateAndNormalize(double[])} expands the running
 * min/max with the new sample and returns the normalized values in [0, 1].
 *
 * When only one distinct value has been seen (min == max), returns 0.5
 * so the bandit starts with a neutral, unbiased feature.
 */
public class RunningNormalizer {

    private static final double EPS = 1e-10;

    private final double[] min;
    private final double[] max;

    public RunningNormalizer(int featureSize) {
        this.min = new double[featureSize];
        this.max = new double[featureSize];
        Arrays.fill(min, Double.MAX_VALUE);
        Arrays.fill(max, -Double.MAX_VALUE);
    }

    /**
     * Updates running min/max with the sample, then returns normalized values.
     * Input length must equal featureSize supplied at construction.
     */
    public double[] updateAndNormalize(double[] raw) {
        double[] normalized = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] < min[i]) min[i] = raw[i];
            if (raw[i] > max[i]) max[i] = raw[i];

            double range = max[i] - min[i];
            if (range < EPS) {
                normalized[i] = 0.5;
            } else {
                normalized[i] = (raw[i] - min[i]) / range;
            }
        }
        return normalized;
    }
}
