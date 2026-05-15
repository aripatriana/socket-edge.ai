package com.socket.edge.ai.unit;

import com.socket.edge.ai.reward.ChannelState;
import com.socket.edge.ai.reward.RewardCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RewardCalculator")
class RewardCalculatorTest {

    private RewardCalculator calc;

    @BeforeEach
    void setUp() { calc = new RewardCalculator(); }

    @Test
    @DisplayName("latency decrease → positive reward")
    void latencyDecreasePositiveReward() {
        ChannelState prev = new ChannelState("ch", 10_000, 100, 80);
        ChannelState curr = new ChannelState("ch",  5_000, 100, 80); // latency halved

        double reward = calc.compute(prev, curr);

        assertThat(reward).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("latency increase → negative reward")
    void latencyIncreaseNegativeReward() {
        ChannelState prev = new ChannelState("ch", 5_000, 100, 80);
        ChannelState curr = new ChannelState("ch", 20_000, 100, 80); // latency 4×

        double reward = calc.compute(prev, curr);

        assertThat(reward).isLessThan(0.0);
    }

    @Test
    @DisplayName("throughput increase → positive reward")
    void throughputIncreasePositiveReward() {
        ChannelState prev = new ChannelState("ch", 5_000, 50,  80);
        ChannelState curr = new ChannelState("ch", 5_000, 150, 80); // throughput 3×

        double reward = calc.compute(prev, curr);

        assertThat(reward).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("pressure decrease → positive reward")
    void pressureDecreasePositiveReward() {
        ChannelState prev = new ChannelState("ch", 5_000, 100, 200);
        ChannelState curr = new ChannelState("ch", 5_000, 100,  50); // pressure dropped

        double reward = calc.compute(prev, curr);

        assertThat(reward).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("no change → reward = 0.0")
    void noChangeZeroReward() {
        ChannelState state = new ChannelState("ch", 5_000, 100, 80);

        // First call establishes running max-delta
        calc.compute(state, new ChannelState("ch", 6_000, 100, 80));

        // No change from one state to the same
        double reward = calc.compute(state, state);
        assertThat(reward).isEqualTo(0.0);
    }

    @Test
    @DisplayName("reward is always clamped to [-1.0, +1.0]")
    void rewardClamped() {
        // Extreme improvement: latency drops from 1_000_000 to 1 — would produce very large delta
        ChannelState prev = new ChannelState("ch", 1_000_000_000L, 1,   1);
        ChannelState curr = new ChannelState("ch", 1,              1000, 1);

        double reward = calc.compute(prev, curr);

        assertThat(reward).isBetween(-1.0, 1.0);
    }

    @Test
    @DisplayName("independent per channel — different channels don't share running max-delta")
    void independentPerChannel() {
        ChannelState chA_prev = new ChannelState("channelA", 100_000, 100, 80);
        ChannelState chA_curr = new ChannelState("channelA",  50_000, 100, 80);

        ChannelState chB_prev = new ChannelState("channelB", 1_000_000, 100, 80);
        ChannelState chB_curr = new ChannelState("channelB",   500_000, 100, 80);

        double rewardA = calc.compute(chA_prev, chA_curr);
        double rewardB = calc.compute(chB_prev, chB_curr);

        // Both have same relative improvement (50%) → same normalized reward
        assertThat(rewardA).isCloseTo(rewardB, within(1e-6));
    }
}
