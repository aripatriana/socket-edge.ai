package com.socket.edge.ai.bandit;

import org.apache.commons.math3.linear.*;

/**
 * LinUCB bandit model for one channel.
 *
 * State: A (featureSize×featureSize), b (featureSize), cached A⁻¹.
 * A starts as identity; b starts as zeros.
 *
 * Per-endpoint scoring uses the block corresponding to endpoint i
 * from the shared A⁻¹ and θ = A⁻¹·b, isolating that endpoint's
 * contribution while preserving cross-endpoint correlation in A.
 *
 * Update equation (applied per MetricsBundle with the full context vector):
 *   A ← A + x·xᵀ
 *   b ← b + reward·x
 *   θ ← A⁻¹·b  (recomputed lazily via cached A⁻¹)
 */
public class ChannelBanditModel {

    private static final int FEATURES_PER_ENDPOINT = 15;

    private final int    endpointCount;
    private final int    featureSize;
    private final double alpha;

    private RealMatrix A;
    private RealVector b;
    private RealMatrix A_inv;

    public ChannelBanditModel(int endpointCount, double alpha) {
        this.endpointCount = endpointCount;
        this.featureSize   = endpointCount * FEATURES_PER_ENDPOINT;
        this.alpha         = alpha;
        reset();
    }

    /**
     * Computes the LinUCB score for endpoint {@code epIdx} given full context vector {@code x}.
     *
     * score = (θ_i · x_i) + α · √(x_iᵀ · A_ii⁻¹ · x_i)
     *
     * where _i denotes the block [epIdx*15 .. epIdx*15+14] of the respective vectors/matrix.
     */
    public double score(int epIdx, double[] x) {
        int start = epIdx * FEATURES_PER_ENDPOINT;
        RealVector fullX = new ArrayRealVector(x, false);
        RealVector x_i   = fullX.getSubVector(start, FEATURES_PER_ENDPOINT);

        RealVector theta  = A_inv.operate(b);
        RealVector theta_i = theta.getSubVector(start, FEATURES_PER_ENDPOINT);

        RealMatrix A_inv_block = A_inv.getSubMatrix(start, start + FEATURES_PER_ENDPOINT - 1,
                                                    start, start + FEATURES_PER_ENDPOINT - 1);

        double exploit = theta_i.dotProduct(x_i);
        double variance = x_i.dotProduct(A_inv_block.operate(x_i));
        double explore  = alpha * Math.sqrt(Math.max(0.0, variance));

        return exploit + explore;
    }

    /**
     * Online update given the full context vector and the channel-level reward.
     *
     * A ← A + x·xᵀ
     * b ← b + reward·x
     * A⁻¹ recomputed via LU decomposition.
     */
    public void update(double[] x, double reward) {
        RealVector xVec = new ArrayRealVector(x, false);
        A = A.add(xVec.outerProduct(xVec));
        b = b.add(xVec.mapMultiply(reward));
        A_inv = new LUDecomposition(A).getSolver().getInverse();
    }

    public int endpointCount() { return endpointCount; }

    /** Resets state to identity/zeros — called when topology changes. */
    private void reset() {
        A     = MatrixUtils.createRealIdentityMatrix(featureSize);
        b     = new ArrayRealVector(featureSize);
        A_inv = A; // identity is self-inverse
    }
}
