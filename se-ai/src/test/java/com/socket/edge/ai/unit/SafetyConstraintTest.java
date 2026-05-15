package com.socket.edge.ai.unit;

import com.socket.edge.ai.safety.SafetyConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SafetyConstraint")
class SafetyConstraintTest {

    private final SafetyConstraint safety = new SafetyConstraint(5, 80);

    @Test
    @DisplayName("output always sums to 100")
    void outputSumsTo100() {
        Map<String, Integer> weights = Map.of("a", 40, "b", 40, "c", 20);
        Map<String, Integer> result  = safety.apply(weights, Set.of());

        assertThat(sum(result)).isEqualTo(100);
    }

    @Test
    @DisplayName("no endpoint below minWeight (5) when all are ACTIVE")
    void noBelowMinWeight() {
        // a has softmax=1 which would round to 1 — must be raised to 5
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("a", 1);
        weights.put("b", 50);
        weights.put("c", 49);

        Map<String, Integer> result = safety.apply(weights, Set.of());

        result.forEach((k, v) -> assertThat(v).isGreaterThanOrEqualTo(5));
        assertThat(sum(result)).isEqualTo(100);
    }

    @Test
    @DisplayName("no endpoint above maxWeight (80) when all are ACTIVE")
    void noAboveMaxWeight() {
        // a monopolizes — must be capped at 80
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("a", 95);
        weights.put("b",  3);
        weights.put("c",  2);

        Map<String, Integer> result = safety.apply(weights, Set.of());

        result.forEach((k, v) -> assertThat(v).isLessThanOrEqualTo(80));
        assertThat(sum(result)).isEqualTo(100);
    }

    @Test
    @DisplayName("DOWN endpoint gets weight 0 regardless of softmax score")
    void downEndpointGetsZero() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("a", 60);
        weights.put("b", 25);
        weights.put("c", 15);

        Map<String, Integer> result = safety.apply(weights, Set.of("a")); // a is DOWN

        assertThat(result.get("a")).isEqualTo(0);
        assertThat(sum(result)).isEqualTo(100);
    }

    @Test
    @DisplayName("all DOWN → equal distribution (se-core decides what to do)")
    void allDownEqualDistribution() {
        Map<String, Integer> weights = Map.of("a", 50, "b", 30, "c", 20);

        Map<String, Integer> result = safety.apply(weights, Set.of("a", "b", "c"));

        // equal split: 33 + 33 + 34 = 100 (or similar floor+remainder distribution)
        assertThat(sum(result)).isEqualTo(100);
        int min = result.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = result.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("two endpoints: one DOWN, one gets 100")
    void oneDownOtherGets100() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("a", 60);
        weights.put("b", 40);

        Map<String, Integer> result = safety.apply(weights, Set.of("b"));

        assertThat(result.get("a")).isEqualTo(100);
        assertThat(result.get("b")).isEqualTo(0);
    }

    private static int sum(Map<String, Integer> m) {
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }
}
