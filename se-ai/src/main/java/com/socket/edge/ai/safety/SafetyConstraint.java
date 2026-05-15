package com.socket.edge.ai.safety;

import java.util.*;

/**
 * Enforces hard weight boundaries after softmax selection.
 *
 * Rules applied in order:
 *   1. DOWN endpoints → weight forced to 0 (excluded from routing).
 *   2. Active endpoints constrained to [minWeight, maxWeight] using an
 *      iterative phase-based algorithm:
 *      Phase 1 — clip above-max endpoints, redistribute remaining budget
 *                to uncapped endpoints proportionally.
 *      Phase 2 — clip below-min endpoints (after redistribution may exist),
 *                redistribute remaining budget to unclamped endpoints.
 *   3. Integer weights with floor + remainder distributed by largest fractional part.
 *   4. Sum is guaranteed to be exactly 100.
 *
 * Single active endpoint: always gets 100 (safety bounds not applicable).
 */
public class SafetyConstraint {

    private final int minWeight;
    private final int maxWeight;

    public SafetyConstraint(int minWeight, int maxWeight) {
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
    }

    /**
     * @param weights     hashId → proposed integer weight (from softmax, sum = 100)
     * @param downSockets set of hash_ids currently in DOWN or ERROR state
     * @return adjusted hashId → weight, sum = 100
     */
    public Map<String, Integer> apply(Map<String, Integer> weights, Set<String> downSockets) {
        List<String> active = new ArrayList<>();
        for (String id : weights.keySet()) {
            if (!downSockets.contains(id)) active.add(id);
        }

        if (active.isEmpty()) return equalDistribution(weights.keySet());

        // Single active endpoint always gets everything
        if (active.size() == 1) {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String k : weights.keySet()) result.put(k, 0);
            result.put(active.get(0), 100);
            return result;
        }

        int activeSum = active.stream().mapToInt(weights::get).sum();
        if (activeSum == 0) return equalDistribution(new HashSet<>(active));

        // Scale active weights proportionally to sum = 100.0
        double[] w = new double[active.size()];
        double scale = 100.0 / activeSum;
        for (int i = 0; i < active.size(); i++) {
            w[i] = weights.get(active.get(i)) * scale;
        }

        // Phase 1: enforce max — pin over-max endpoints, redistribute to others
        clip(w, false); // max only pass

        // Phase 2: enforce min — pin under-min endpoints, redistribute to others
        clip(w, true);  // min only pass

        return buildResult(weights, active, downSockets, w);
    }

    /**
     * Single-phase iterative clip.
     * If {@code minPass}: clips below min only and redistributes excess to free endpoints.
     * If {@code !minPass}: clips above max only and redistributes to free endpoints.
     */
    private void clip(double[] w, boolean minPass) {
        boolean[] pinned = new boolean[w.length];

        for (int pass = 0; pass <= w.length; pass++) {
            boolean changed = false;

            for (int i = 0; i < w.length; i++) {
                if (pinned[i]) continue;
                if (!minPass && w[i] > maxWeight) {
                    w[i] = maxWeight;
                    pinned[i] = true;
                    changed = true;
                } else if (minPass && w[i] < minWeight) {
                    w[i] = minWeight;
                    pinned[i] = true;
                    changed = true;
                }
            }
            if (!changed) break;

            // Redistribute remaining budget to free endpoints
            double pinnedSum = 0, freeSum = 0;
            int freeCount = 0;
            for (int i = 0; i < w.length; i++) {
                if (pinned[i]) pinnedSum += w[i];
                else { freeSum += w[i]; freeCount++; }
            }
            if (freeCount == 0) break; // all pinned, can't redistribute

            double remaining = 100.0 - pinnedSum;
            double freeScale = (freeSum > 1e-10) ? remaining / freeSum : remaining / freeCount;
            for (int i = 0; i < w.length; i++) {
                if (!pinned[i]) {
                    w[i] = (freeSum > 1e-10) ? w[i] * freeScale : freeScale;
                }
            }
        }
    }

    private Map<String, Integer> buildResult(Map<String, Integer> original,
                                             List<String> active,
                                             Set<String> downSockets,
                                             double[] w) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String k : original.keySet()) result.put(k, 0); // init DOWN = 0

        double[] remainders = new double[active.size()];
        int total = 0;
        for (int i = 0; i < active.size(); i++) {
            int floored = (int) w[i];
            result.put(active.get(i), floored);
            remainders[i] = w[i] - floored;
            total += floored;
        }

        // Distribute leftover points by largest fractional remainder
        int leftover = 100 - total;
        Integer[] order = new Integer[active.size()];
        for (int i = 0; i < active.size(); i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(remainders[b], remainders[a]));
        for (int i = 0; i < leftover && i < active.size(); i++) {
            String id = active.get(order[i]);
            result.put(id, result.get(id) + 1);
        }
        return result;
    }

    private Map<String, Integer> equalDistribution(Set<String> ids) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int n = ids.size();
        if (n == 0) return result;
        int base = 100 / n;
        int extra = 100 % n;
        int i = 0;
        for (String id : ids) {
            result.put(id, base + (i++ < extra ? 1 : 0));
        }
        return result;
    }
}
