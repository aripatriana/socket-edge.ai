package com.socket.edge.core;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe store for AI-computed routing weights received via {@code UpdateWeight} gRPC.
 *
 * <p>Keys: channel name → (binding_id → weight). Weights are replaced atomically per channel
 * on each AI update cycle. The routing strategy reads from here on every request.</p>
 *
 * <p>Version counter increments on every {@link #update} so that cached routing cycles
 * can detect stale entries and rebuild.</p>
 */
public class AiWeightRegistry {

    private final ConcurrentHashMap<String, Map<String, Integer>> store = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);

    /** Replaces the weight map for the given channel atomically and bumps the global version. */
    public void update(String channelName, Map<String, Integer> weights) {
        store.put(channelName, Collections.unmodifiableMap(weights));
        version.incrementAndGet();
    }

    /**
     * Returns the current weights for a channel, or an empty map if no AI update
     * has been received yet (causes the strategy to fall back to round-robin).
     */
    public Map<String, Integer> get(String channelName) {
        return store.getOrDefault(channelName, Map.of());
    }

    public boolean hasWeights(String channelName) {
        return store.containsKey(channelName);
    }

    /** Monotonically increasing counter — bumped on every {@link #update}. */
    public long version() {
        return version.get();
    }
}
