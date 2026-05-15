package com.socket.edge.ai.bandit;

import java.util.*;

/**
 * Converts LinUCB scores into integer weights that sum to 100.
 *
 * Algorithm:
 *   1. Softmax over raw scores (numerically stable via max subtraction).
 *   2. Multiply each probability by 100 and floor to integer.
 *   3. Distribute any remaining points (due to floor rounding) to
 *      the endpoints with the highest fractional remainders.
 */
public class SoftmaxWeightSelector {

    /**
     * @param scores  non-empty list of (hashId, score) pairs
     * @return map hashId → integer weight; weights sum to exactly 100
     */
    public Map<String, Integer> select(List<EndpointScore> scores) {
        int n = scores.size();
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) raw[i] = scores.get(i).score();

        double[] softmax = softmax(raw);

        // floor each weight
        int[] weights = new int[n];
        double[] remainders = new double[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            double w = softmax[i] * 100.0;
            weights[i]    = (int) w;
            remainders[i] = w - weights[i];
            total += weights[i];
        }

        // distribute leftover to highest remainders
        int leftover = 100 - total;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(remainders[b], remainders[a]));
        for (int i = 0; i < leftover; i++) weights[order[i]]++;

        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) result.put(scores.get(i).hashId(), weights[i]);
        return result;
    }

    private double[] softmax(double[] scores) {
        double max = Double.NEGATIVE_INFINITY;
        for (double s : scores) if (s > max) max = s;

        double[] exp = new double[scores.length];
        double sum = 0;
        for (int i = 0; i < scores.length; i++) {
            exp[i] = Math.exp(scores[i] - max);
            sum += exp[i];
        }
        for (int i = 0; i < exp.length; i++) exp[i] /= sum;
        return exp;
    }
}
