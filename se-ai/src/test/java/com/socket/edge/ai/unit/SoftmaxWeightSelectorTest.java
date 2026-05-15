package com.socket.edge.ai.unit;

import com.socket.edge.ai.bandit.EndpointScore;
import com.socket.edge.ai.bandit.SoftmaxWeightSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SoftmaxWeightSelector")
class SoftmaxWeightSelectorTest {

    private final SoftmaxWeightSelector selector = new SoftmaxWeightSelector();

    @Test
    @DisplayName("weights always sum to exactly 100")
    void weightsSumTo100() {
        Map<String, Integer> weights = selector.select(List.of(
                new EndpointScore("a", 1.2),
                new EndpointScore("b", 0.8),
                new EndpointScore("c", 2.0)
        ));

        int sum = weights.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(sum).isEqualTo(100);
    }

    @Test
    @DisplayName("equal scores → weights differ by at most 1 (rounding only)")
    void equalScoresProduceNearEqualWeights() {
        Map<String, Integer> weights = selector.select(List.of(
                new EndpointScore("ep1", 0.5),
                new EndpointScore("ep2", 0.5),
                new EndpointScore("ep3", 0.5),
                new EndpointScore("ep4", 0.5)
        ));

        int min = weights.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = weights.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("dominant score wins majority share")
    void dominantScoreGetsMajority() {
        Map<String, Integer> weights = selector.select(List.of(
                new EndpointScore("winner", 100.0),   // hugely better
                new EndpointScore("loser1",   0.0),
                new EndpointScore("loser2",   0.0)
        ));

        assertThat(weights.get("winner")).isGreaterThan(60);
        assertThat(weights.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }

    @Test
    @DisplayName("all weights are non-negative")
    void allWeightsNonNegative() {
        Map<String, Integer> weights = selector.select(List.of(
                new EndpointScore("a", -5.0),
                new EndpointScore("b", -1.0),
                new EndpointScore("c",  0.0)
        ));

        weights.values().forEach(w -> assertThat(w).isGreaterThanOrEqualTo(0));
        assertThat(weights.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
    }

    @Test
    @DisplayName("single endpoint gets 100")
    void singleEndpointGets100() {
        Map<String, Integer> weights = selector.select(List.of(
                new EndpointScore("only", 1.5)
        ));

        assertThat(weights.get("only")).isEqualTo(100);
    }

    @Test
    @DisplayName("output preserves all input hashIds")
    void allHashIdsPresent() {
        List<EndpointScore> scores = List.of(
                new EndpointScore("x1", 1.0),
                new EndpointScore("x2", 2.0),
                new EndpointScore("x3", 0.5)
        );

        Map<String, Integer> weights = selector.select(scores);

        assertThat(weights).containsKeys("x1", "x2", "x3");
    }
}
