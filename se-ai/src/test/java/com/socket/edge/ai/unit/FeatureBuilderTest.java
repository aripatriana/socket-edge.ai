package com.socket.edge.ai.unit;

import com.socket.edge.ai.feature.FeatureBuilder;
import com.socket.edge.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FeatureBuilder")
class FeatureBuilderTest {

    private FeatureBuilder builder;

    @BeforeEach
    void setUp() { builder = new FeatureBuilder(); }

    @Test
    @DisplayName("feature vector length = N * 15")
    void featureVectorLength() {
        List<SocketSnapshot> endpoints = List.of(ep("a", 1000, 50, 30), ep("b", 2000, 60, 40));
        double[] x = builder.build("ch", endpoints);

        assertThat(x).hasSize(2 * FeatureBuilder.FEATURES_PER_ENDPOINT);
    }

    @Test
    @DisplayName("3 endpoints → 45 features")
    void threeEndpoints45Features() {
        List<SocketSnapshot> endpoints = List.of(
                ep("a", 1000, 50, 30),
                ep("b", 2000, 60, 40),
                ep("c", 5000, 40, 20)
        );
        double[] x = builder.build("ch", endpoints);

        assertThat(x).hasSize(45);
    }

    @Test
    @DisplayName("all features in [0, 1] after multiple calls with varied metrics")
    void featuresNormalized() {
        List<SocketSnapshot> eps = List.of(ep("a", 1000, 50, 30), ep("b", 9000, 100, 80));

        // First call: min == max → 0.5
        double[] x1 = builder.build("ch", eps);
        for (double v : x1) assertThat(v).isEqualTo(0.5);

        // Second call with same values: still 0.5 (no change)
        double[] x2 = builder.build("ch", eps);
        for (double v : x2) assertThat(v).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("topology change resets normalizer — features restart from 0.5")
    void topologyChangeResetsNormalizer() {
        // 2 endpoints — build range
        List<SocketSnapshot> two = List.of(ep("a", 1000, 50, 30), ep("b", 9000, 100, 80));
        builder.build("ch", two);
        builder.build("ch", two); // second call shifts normalizer

        // switch to 3 endpoints → normalizer should reset
        List<SocketSnapshot> three = List.of(
                ep("a", 1000, 50, 30),
                ep("b", 5000, 70, 50),
                ep("c", 9000, 100, 80)
        );
        double[] x = builder.build("ch", three);

        // After topology change, first call → all 0.5
        for (double v : x) assertThat(v).isEqualTo(0.5);
        assertThat(x).hasSize(45);
    }

    @Test
    @DisplayName("endpoints sorted by binding_id → feature order is stable across calls")
    void stableFeatureOrder() {
        // ep "zzz" and "aaa" in different order
        List<SocketSnapshot> order1 = List.of(ep("zzz", 1000, 50, 30), ep("aaa", 9000, 100, 80));
        List<SocketSnapshot> order2 = List.of(ep("aaa", 9000, 100, 80), ep("zzz", 1000, 50, 30));

        // Sort both as the engine does, then build
        List<SocketSnapshot> sorted1 = order1.stream()
                .sorted(java.util.Comparator.comparing(SocketSnapshot::getBindingId)).toList();
        List<SocketSnapshot> sorted2 = order2.stream()
                .sorted(java.util.Comparator.comparing(SocketSnapshot::getBindingId)).toList();

        double[] x1 = builder.build("stable", sorted1);

        FeatureBuilder builder2 = new FeatureBuilder();
        double[] x2 = builder2.build("stable", sorted2);

        assertThat(x1).containsExactly(x2);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static SocketSnapshot ep(String bindingId, long latAvg, long thrAvg, long presAvg) {
        long latMin = (long)(latAvg * 0.5), latMax = latAvg * 2, latP90 = (long)(latAvg * 1.4), latP95 = (long)(latAvg * 1.6);
        long thrMin = (long)(thrAvg * 0.5), thrMax = thrAvg * 2, thrP90 = (long)(thrAvg * 1.2), thrP95 = (long)(thrAvg * 1.3);
        long prMin  = (long)(presAvg* 0.5), prMax  = presAvg* 2, prP90  = (long)(presAvg* 1.2), prP95  = (long)(presAvg* 1.3);

        return SocketSnapshot.newBuilder()
                .setBindingId(bindingId)
                .setType("CLIENT")
                .setName("ch")
                .setRuntime(SocketRuntime.newBuilder().setState("ACTIVE").build())
                .setMetrics(SocketMetrics.newBuilder()
                        .setLatencyNs(stat(latAvg, latMin, latMax, latP90, latP95))
                        .setThroughputTps(stat(thrAvg, thrMin, thrMax, thrP90, thrP95))
                        .setPressureTps(stat(presAvg, prMin, prMax, prP90, prP95))
                        .build())
                .build();
    }

    private static StatSummary stat(long avg, long min, long max, long p90, long p95) {
        return StatSummary.newBuilder().setAvg(avg).setMin(min).setMax(max).setP90(p90).setP95(p95).build();
    }
}
