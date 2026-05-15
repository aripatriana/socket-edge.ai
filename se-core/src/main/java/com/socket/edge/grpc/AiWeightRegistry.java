package com.socket.edge.grpc;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe store for AI-computed routing weights received via UpdateWeight gRPC.
 *
 * Keys: channel name → (hash_id → weight).
 *
 * The routing strategy reads from here when selecting endpoints.
 * Weights are replaced atomically per channel on each AI update.
 */
public class AiWeightRegistry {

    private final ConcurrentHashMap<String, Map<String, Integer>> store = new ConcurrentHashMap<>();

    /** Replaces the weight map for the given channel atomically. */
    public void update(String channelName, Map<String, Integer> weights) {
        store.put(channelName, Collections.unmodifiableMap(weights));
    }

    /**
     * Returns the current weights for a channel, or an empty map if
     * no AI update has been received yet (fall back to default strategy).
     */
    public Map<String, Integer> get(String channelName) {
        return store.getOrDefault(channelName, Map.of());
    }

    public boolean hasWeights(String channelName) {
        return store.containsKey(channelName);
    }
}
