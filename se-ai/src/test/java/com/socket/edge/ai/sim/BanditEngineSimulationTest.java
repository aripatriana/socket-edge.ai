package com.socket.edge.ai.sim;

import com.socket.edge.ai.bandit.SoftmaxWeightSelector;
import com.socket.edge.ai.engine.BanditEngine;
import com.socket.edge.ai.engine.Publisher;
import com.socket.edge.ai.feature.FeatureBuilder;
import com.socket.edge.ai.reward.RewardCalculator;
import com.socket.edge.ai.safety.SafetyConstraint;
import com.socket.edge.grpc.MetricsBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.socket.edge.ai.sim.MetricBundleFactory.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Simulation test — drives BanditEngine with synthetic MetricsBundles and
 * asserts on weight publications captured by CapturingPublisher.
 *
 * Covers:
 *   - weights published every bundle (even first, before any reward signal)
 *   - weights always sum to 100
 *   - safety bounds [5, 80] respected for ACTIVE endpoints
 *   - DOWN endpoint forced to 0, others redistribute to 100
 *   - single-endpoint channel skipped (no publication)
 *   - multi-channel bundle publishes for each channel independently
 *   - server sockets filtered out (only CLIENT sockets processed)
 *   - topology change (endpoint added) triggers model reset
 */
@DisplayName("BanditEngine — LinUCB metric simulation")
class BanditEngineSimulationTest {

    // ── Capturing publisher ───────────────────────────────────────────────────

    record WeightCapture(String channel, Map<String, Integer> weights,
                         double reward, double confidence) {}

    static class CapturingPublisher implements Publisher {
        final List<WeightCapture> all = new ArrayList<>();

        @Override
        public void publish(String channelName, Map<String, Integer> weights,
                            double reward, double confidence) {
            all.add(new WeightCapture(channelName, new LinkedHashMap<>(weights), reward, confidence));
        }

        List<WeightCapture> forChannel(String ch) {
            return all.stream().filter(c -> c.channel().equals(ch)).toList();
        }

        WeightCapture last(String ch) {
            List<WeightCapture> list = forChannel(ch);
            return list.isEmpty() ? null : list.get(list.size() - 1);
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    CapturingPublisher publisher;
    BanditEngine       engine;

    @BeforeEach
    void setUp() {
        publisher = new CapturingPublisher();
        engine    = new BanditEngine(
                new FeatureBuilder(),
                new SoftmaxWeightSelector(),
                new RewardCalculator(),
                new SafetyConstraint(5, 80),
                publisher,
                0.5
        );
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishes weights for every bundle, weights sum to 100")
    void publishedEveryBundleAndSumsTo100() {
        int bundles = 6;
        for (int i = 0; i < bundles; i++) {
            engine.process(fellaBundle("ACTIVE", "ACTIVE", "ACTIVE"));
        }

        List<WeightCapture> captures = publisher.forChannel("fello");
        assertThat(captures).hasSize(bundles);

        for (WeightCapture c : captures) {
            int sum = c.weights().values().stream().mapToInt(Integer::intValue).sum();
            assertThat(sum).as("weights must sum to 100 at each iteration").isEqualTo(100);
        }
    }

    @Test
    @DisplayName("all ACTIVE endpoints get weight in [5, 80]")
    void activeSafetyBoundsRespected() {
        for (int i = 0; i < 5; i++) {
            engine.process(fellaBundle("ACTIVE", "ACTIVE", "ACTIVE"));
        }

        publisher.forChannel("fello").forEach(c ->
                c.weights().values().forEach(w ->
                        assertThat(w).as("weight must be in [5, 80]").isBetween(5, 80)
                )
        );
    }

    @Test
    @DisplayName("DOWN endpoint gets weight=0; remaining sum to 100")
    void downEndpointGetsZeroWeight() {
        engine.process(fellaBundle("ACTIVE", "ACTIVE", "ACTIVE"));

        // second bundle: ep1 goes DOWN
        engine.process(fellaBundle("DOWN", "ACTIVE", "ACTIVE"));

        WeightCapture last = publisher.last("fello");
        assertThat(last).isNotNull();
        assertThat(last.weights().get("ep1")).as("DOWN endpoint must get weight 0").isEqualTo(0);

        int sum = last.weights().values().stream().mapToInt(Integer::intValue).sum();
        assertThat(sum).isEqualTo(100);
    }

    @Test
    @DisplayName("single-endpoint channel is skipped — publisher not called")
    void singleEndpointChannelSkipped() {
        engine.process(bundle(
                client("ep1", "solo", 5_000_000, 50, 50, "ACTIVE")
        ));

        assertThat(publisher.forChannel("solo")).isEmpty();
    }

    @Test
    @DisplayName("multi-channel bundle publishes for each channel")
    void multiChannelBundle() {
        engine.process(bundle(
                // channel fello — 3 endpoints
                client("ep1", "fello", 1_000_000, 100, 80, "ACTIVE"),
                client("ep2", "fello", 3_000_000, 100, 80, "ACTIVE"),
                client("ep3", "fello", 8_000_000, 100, 80, "ACTIVE"),
                // channel bca — 2 endpoints
                client("bca1", "bca", 2_000_000, 80, 60, "ACTIVE"),
                client("bca2", "bca", 4_000_000, 80, 60, "ACTIVE")
        ));

        assertThat(publisher.forChannel("fello")).hasSize(1);
        assertThat(publisher.forChannel("bca")).hasSize(1);

        assertThat(weightSum(publisher.last("fello"))).isEqualTo(100);
        assertThat(weightSum(publisher.last("bca"))).isEqualTo(100);
    }

    @Test
    @DisplayName("SERVER sockets are ignored — only CLIENT sockets count")
    void serverSocketsIgnored() {
        engine.process(bundle(
                server("srv1", "fello"),                          // SERVER — must be ignored
                client("ep1", "fello", 2_000_000, 80, 60, "ACTIVE"),
                client("ep2", "fello", 5_000_000, 80, 60, "ACTIVE")
        ));

        WeightCapture cap = publisher.last("fello");
        assertThat(cap).isNotNull();
        assertThat(cap.weights()).containsOnlyKeys("ep1", "ep2"); // srv1 absent
        assertThat(weightSum(cap)).isEqualTo(100);
    }

    @Test
    @DisplayName("topology change resets model — weights recomputed from scratch")
    void topologyChangeResetsModel() {
        // 3 endpoints
        for (int i = 0; i < 4; i++) {
            engine.process(fellaBundle("ACTIVE", "ACTIVE", "ACTIVE"));
        }
        int capturesBefore = publisher.forChannel("fello").size();

        // topology changes to 2 endpoints
        for (int i = 0; i < 3; i++) {
            engine.process(bundle(
                    client("ep1", "fello", 1_000_000, 100, 80, "ACTIVE"),
                    client("ep2", "fello", 3_000_000, 100, 80, "ACTIVE")
            ));
        }

        List<WeightCapture> allCaptures = publisher.forChannel("fello");
        assertThat(allCaptures).hasSize(capturesBefore + 3);

        // after topology change, all caps still have sum=100
        allCaptures.forEach(c ->
                assertThat(weightSum(c)).isEqualTo(100)
        );

        // after topology change, only ep1 and ep2 appear
        WeightCapture last = publisher.last("fello");
        assertThat(last.weights()).containsOnlyKeys("ep1", "ep2");
    }

    @Test
    @DisplayName("weights diverge from equal distribution after reward signals accumulate")
    void weightsAdaptAfterRewardSignals() {
        // ep1 constant (1ms), ep2/ep3 degrade each bundle → channel avg latency rises.
        // After enough bundles the model has accumulated reward signal and weights should
        // no longer be equal (max − min > 1, i.e. not just the floor-remainder artefact).
        long[] base = {1_000_000L, 5_000_000L, 20_000_000L};

        engine.process(diverseBundle(base, "ACTIVE")); // baseline

        for (int i = 1; i <= 30; i++) {
            long[] shifted = {
                    base[0],
                    base[1] + (i * 200_000L),
                    base[2] + (i * 1_000_000L)
            };
            engine.process(diverseBundle(shifted, "ACTIVE"));
        }

        WeightCapture last = publisher.last("fello");
        assertThat(last).isNotNull();
        assertThat(weightSum(last)).isEqualTo(100);

        int min = last.weights().values().stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = last.weights().values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertThat(max - min)
                .as("weights should diverge from equal after reward accumulation (max-min > 1)")
                .isGreaterThan(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** fello channel with ep1/ep2/ep3 at 1ms/5ms/15ms, states configurable. */
    private MetricsBundle fellaBundle(String state1, String state2, String state3) {
        return bundle(
                client("ep1", "fello", 1_000_000, 100, 80, state1),
                client("ep2", "fello", 5_000_000, 100, 80, state2),
                client("ep3", "fello", 15_000_000, 100, 80, state3)
        );
    }

    /** fello channel with configurable latencies per endpoint. */
    private MetricsBundle diverseBundle(long[] latencies, String state) {
        return bundle(
                client("ep1", "fello", latencies[0], 100, 80, state),
                client("ep2", "fello", latencies[1], 100, 80, state),
                client("ep3", "fello", latencies[2], 100, 80, state)
        );
    }

    private static int weightSum(WeightCapture c) {
        return c.weights().values().stream().mapToInt(Integer::intValue).sum();
    }
}
