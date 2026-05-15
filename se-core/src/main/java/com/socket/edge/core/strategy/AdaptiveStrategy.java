package com.socket.edge.core.strategy;

import com.socket.edge.core.AiWeightRegistry;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.SocketChannel;
import com.socket.edge.model.VersionedCandidates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted-random selection strategy driven by AI weights from {@link AiWeightRegistry}.
 *
 * <p>On each request:
 * <ol>
 *   <li>Reads the current weight map for this channel from the registry.</li>
 *   <li>Assigns each candidate the AI weight keyed by its {@code binding_id}.</li>
 *   <li>Performs a weighted random draw in O(n) time.</li>
 * </ol>
 *
 * <p>Fallback to round-robin in two cases:
 * <ul>
 *   <li>No AI weights have been published yet for this channel.</li>
 *   <li>All live candidates have zero AI weight (e.g. every endpoint is DOWN
 *       and the AI assigned weight=0, but the health-check pool still lists them).</li>
 * </ul>
 *
 * <p>Thread-safe: the registry is a {@link java.util.concurrent.ConcurrentHashMap},
 * the draw uses {@link ThreadLocalRandom}, and the fallback round-robin is
 * independently thread-safe.
 */
public final class AdaptiveStrategy implements SelectionStrategy<SocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveStrategy.class);

    private final String channelName;
    private final AiWeightRegistry registry;
    private final RoundRobinStrategy<SocketChannel> fallback = new RoundRobinStrategy<>();

    public AdaptiveStrategy(String channelName, AiWeightRegistry registry) {
        this.channelName = channelName;
        this.registry    = registry;
    }

    @Override
    public SocketChannel next(VersionedCandidates<SocketChannel> vc, MessageContext ctx) {
        validate(vc.candidates());

        Map<String, Integer> aiWeights = registry.get(channelName);
        if (aiWeights.isEmpty()) {
            return fallback.next(vc, ctx);
        }

        List<SocketChannel> candidates = vc.candidates();
        int[] weights = new int[candidates.size()];
        int total = 0;

        for (int i = 0; i < candidates.size(); i++) {
            int w = aiWeights.getOrDefault(candidates.get(i).getBindingId(), 0);
            weights[i] = w;
            total += w;
        }

        if (total == 0) {
            if (log.isDebugEnabled()) {
                log.debug("channel={} all AI weights are zero, falling back to round-robin", channelName);
            }
            return fallback.next(vc, ctx);
        }

        int r = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (r < cumulative) return candidates.get(i);
        }

        return candidates.get(candidates.size() - 1);
    }
}
