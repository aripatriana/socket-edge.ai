package com.socket.edge.ai.unit;

import com.socket.edge.ai.feature.RunningNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RunningNormalizer")
class RunningNormalizerTest {

    @Test
    @DisplayName("first value → 0.5 (no range established yet)")
    void firstValueReturnsMidpoint() {
        RunningNormalizer n = new RunningNormalizer(2);
        double[] result = n.updateAndNormalize(new double[]{1000.0, 50.0});

        assertThat(result[0]).isEqualTo(0.5);
        assertThat(result[1]).isEqualTo(0.5);
    }

    @Test
    @DisplayName("two distinct values → min normalizes to 0, max to 1")
    void twoValuesNormalizesToZeroAndOne() {
        RunningNormalizer n = new RunningNormalizer(1);

        n.updateAndNormalize(new double[]{10.0}); // establishes min=10, max=10 → 0.5
        double[] second = n.updateAndNormalize(new double[]{20.0}); // max now 20

        // 10→0.0 has already passed; current call is 20→1.0
        assertThat(second[0]).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("values between min and max → normalized in (0, 1)")
    void midpointValueNormalizedCorrectly() {
        RunningNormalizer n = new RunningNormalizer(1);

        n.updateAndNormalize(new double[]{0.0});   // min=0
        n.updateAndNormalize(new double[]{100.0}); // max=100
        double[] result = n.updateAndNormalize(new double[]{50.0});

        assertThat(result[0]).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("identical repeated values stay at 0.5")
    void repeatedSameValueStaysMidpoint() {
        RunningNormalizer n = new RunningNormalizer(1);
        for (int i = 0; i < 10; i++) {
            double[] result = n.updateAndNormalize(new double[]{42.0});
            assertThat(result[0]).isEqualTo(0.5);
        }
    }

    @Test
    @DisplayName("normalization is independent per feature position")
    void perPositionIndependence() {
        RunningNormalizer n = new RunningNormalizer(2);

        // pos 0: range [0, 100]  pos 1: range [1000, 2000]
        n.updateAndNormalize(new double[]{0.0, 1000.0});
        n.updateAndNormalize(new double[]{100.0, 2000.0});
        double[] result = n.updateAndNormalize(new double[]{50.0, 1500.0});

        assertThat(result[0]).isCloseTo(0.5, within(1e-9));
        assertThat(result[1]).isCloseTo(0.5, within(1e-9));
    }
}
