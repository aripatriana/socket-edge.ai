package com.socket.edge.ai.unit;

import com.socket.edge.ai.bandit.ChannelBanditModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ChannelBanditModel — LinUCB math")
class ChannelBanditModelTest {

    private static final int    EP = 2;      // 2 endpoints
    private static final double ALPHA = 0.5;

    @Test
    @DisplayName("fresh model (identity A, zero b): all endpoints get same score for equal feature vectors")
    void freshModelEqualScoresForEqualVectors() {
        ChannelBanditModel model = new ChannelBanditModel(EP, ALPHA);

        // Both endpoints have identical normalized features
        double[] x = uniform(EP, 15, 0.5);

        double score0 = model.score(0, x);
        double score1 = model.score(1, x);

        assertThat(score0).isCloseTo(score1, within(1e-9));
    }

    @Test
    @DisplayName("fresh model: explore > 0 — model is not stuck at zero")
    void freshModelExplorePositive() {
        ChannelBanditModel model = new ChannelBanditModel(EP, ALPHA);
        double[] x = uniform(EP, 15, 0.5);

        double score = model.score(0, x);

        // exploit = 0 (θ = 0), explore = α * sqrt(||x_i||²) > 0
        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("positive reward update scores higher than equivalent negative reward update")
    void positiveRewardRaisesExploit() {
        // Both models receive the same x vector → same A → same A_inv → same explore term.
        // Only θ differs (positive vs negative b), so the exploit drives the difference.
        double[] xUpdate = endpointActiveVector(EP, 15, 0, 1.0);
        double[] xEval   = uniform(EP, 15, 0.5);

        ChannelBanditModel modelPos = new ChannelBanditModel(EP, ALPHA);
        ChannelBanditModel modelNeg = new ChannelBanditModel(EP, ALPHA);
        for (int i = 0; i < 5; i++) {
            modelPos.update(xUpdate, +1.0);
            modelNeg.update(xUpdate, -1.0);
        }

        double scorePos = modelPos.score(0, xEval);
        double scoreNeg = modelNeg.score(0, xEval);

        assertThat(scorePos)
                .as("positive reward should produce higher score than negative reward (same explore term)")
                .isGreaterThan(scoreNeg);
    }

    @Test
    @DisplayName("negative reward update lowers score of the affected endpoint")
    void negativeRewardLowersScore() {
        ChannelBanditModel model = new ChannelBanditModel(EP, ALPHA);
        double[] x = endpointActiveVector(EP, 15, 0, 1.0);

        double scoreBefore = model.score(0, x);
        model.update(x, -1.0);
        double scoreAfter = model.score(0, x);

        assertThat(scoreAfter).isLessThan(scoreBefore);
    }

    @Test
    @DisplayName("exploration term decreases after many updates (A grows → A⁻¹ shrinks)")
    void explorationDecreasesWithMoreData() {
        ChannelBanditModel model = new ChannelBanditModel(1, ALPHA);
        double[] x = uniform(1, 15, 0.5);

        double firstScore = model.score(0, x);

        // many updates → A accumulates, A⁻¹ shrinks, exploration term shrinks
        for (int i = 0; i < 50; i++) model.update(x, 0.0);

        double laterScore = model.score(0, x);

        // exploit still 0 (reward=0), but explore shrank → total score smaller
        assertThat(laterScore).isLessThan(firstScore);
    }

    @Test
    @DisplayName("endpointCount() matches constructor argument")
    void endpointCountCorrect() {
        assertThat(new ChannelBanditModel(3, ALPHA).endpointCount()).isEqualTo(3);
        assertThat(new ChannelBanditModel(7, ALPHA).endpointCount()).isEqualTo(7);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Feature vector where all features = value for all endpoints. */
    private static double[] uniform(int eps, int featuresPerEp, double value) {
        double[] v = new double[eps * featuresPerEp];
        java.util.Arrays.fill(v, value);
        return v;
    }

    /** Feature vector where only endpoint {@code epIdx}'s block = value; rest = 0. */
    private static double[] endpointActiveVector(int eps, int featuresPerEp, int epIdx, double value) {
        double[] v = new double[eps * featuresPerEp];
        int start = epIdx * featuresPerEp;
        java.util.Arrays.fill(v, start, start + featuresPerEp, value);
        return v;
    }
}
