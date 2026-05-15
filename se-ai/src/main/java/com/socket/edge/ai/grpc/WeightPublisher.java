package com.socket.edge.ai.grpc;

import com.socket.edge.grpc.*;
import io.grpc.ManagedChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Publishes computed weight distributions to se-core via the UpdateWeight RPC.
 *
 * Uses a blocking stub — weight updates are fire-and-forget from the engine's
 * perspective; a failed update is logged and skipped (the previous weights
 * remain in effect on the se-core side).
 */
public class WeightPublisher implements com.socket.edge.ai.engine.Publisher {

    private static final Logger log = LoggerFactory.getLogger(WeightPublisher.class);

    private final CoreServiceGrpc.CoreServiceBlockingStub stub;

    public WeightPublisher(ManagedChannel channel) {
        this.stub = CoreServiceGrpc.newBlockingStub(channel);
    }

    /**
     * @param channelName  logical channel name (e.g. "fello")
     * @param weights      hashId → integer weight; sum = 100
     * @param reward       last computed reward [-1..+1]
     * @param confidence   exploration term of the highest-scored endpoint
     */
    public void publish(String channelName, Map<String, Integer> weights,
                        double reward, double confidence) {
        WeightUpdate.Builder builder = WeightUpdate.newBuilder()
                .setChannelName(channelName)
                .setReward(reward)
                .setConfidence(confidence)
                .setTimestamp(System.currentTimeMillis());

        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            builder.addWeights(EndpointWeight.newBuilder()
                    .setHashId(e.getKey())
                    .setWeight(e.getValue())
                    .build());
        }

        try {
            ControlResponse response = stub.updateWeight(builder.build());
            if (!response.getSuccess()) {
                log.warn("UpdateWeight rejected for channel {}: {}", channelName, response.getMessage());
            } else {
                log.debug("Weights published channel={} reward={} weights={}",
                        channelName, String.format("%.3f", reward), weights);
            }
        } catch (Exception e) {
            log.warn("UpdateWeight failed for channel {}: {}", channelName, e.getMessage());
        }
    }
}
