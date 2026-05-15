package com.socket.edge.core.strategy;

import com.socket.edge.core.AiWeightRegistry;
import com.socket.edge.core.LoadAware;
import com.socket.edge.core.MessageContext;
import com.socket.edge.core.socket.SocketChannel;

import java.util.function.Function;

/**
 * {@code SelectionFactory} is a factory class for creating
 * {@link SelectionStrategy} implementations.
 *
 * <p>
 * This factory centralizes the creation of selection strategies
 * such as round-robin, least-connection, and hash-based routing.
 * </p>
 *
 * <p>
 * Strategy selection is typically driven by configuration values
 * (e.g. channel configuration).
 * </p>
 *
 * @author Ari Patriana
 * @since 1.0.0
 */
public class SelectionFactory {

    /**
     * Creates a round-robin selection strategy.
     *
     * <p>
     * Candidates are selected sequentially in a circular manner.
     * </p>
     *
     * @param <T> candidate type that supports weighted selection
     * @return round-robin selection strategy
     */
    public static <T extends WeightedCandidate> SelectionStrategy<T> roundRobin() {
        return new RoundRobinStrategy<>();
    }

    /**
     * Creates a least-connection selection strategy.
     *
     * <p>
     * Candidates with the smallest number of active connections
     * are preferred.
     * </p>
     *
     * @param <T> candidate type that supports load awareness
     * @return least-connection selection strategy
     */
    public static <T extends LoadAware> SelectionStrategy<T> leastConnection() {
        return new LeastConnectionStrategy<>();
    }

    /**
     * Creates a hash-based selection strategy.
     *
     * <p>
     * Candidate selection is based on a hash key derived from
     * {@link MessageContext}.
     * </p>
     *
     * @param keyExtractor function used to extract a hash key
     *                     from the message context
     * @param <T> candidate type
     * @return hash-based selection strategy
     */
    public static <T> SelectionStrategy<T> hash(Function<MessageContext, String> keyExtractor) {
        return new HashStrategy<>(keyExtractor);
    }

    /**
     * Creates an adaptive selection strategy driven by AI weights from
     * {@link AiWeightRegistry}.
     *
     * @param channelName  channel name used to look up weights in the registry
     * @param registry     AI weight registry
     * @return adaptive selection strategy
     */
    public static SelectionStrategy<SocketChannel> adaptive(String channelName, AiWeightRegistry registry) {
        return new AdaptiveStrategy(channelName, registry);
    }

    /**
     * Creates a {@link SelectionStrategy} based on the given strategy name.
     *
     * <p>
     * Supported strategy values:
     * <ul>
     *   <li>{@code "roundrobin"}</li>
     *   <li>{@code "least"}</li>
     *   <li>{@code "hash"}</li>
     * </ul>
     * </p>
     *
     * <p>
     * Strategy name comparison is case-insensitive.
     * </p>
     *
     * @param strategy     strategy identifier
     * @param keyExtractor function to extract hash key
     *                     (required for {@code "hash"} strategy)
     * @param <T> candidate type
     * @return selection strategy instance
     * @throws IllegalArgumentException if strategy is unknown
     */
    @SuppressWarnings("unchecked")
    public static <T> SelectionStrategy<T> create(String strategy, Function<MessageContext, String> keyExtractor) {

        return switch (strategy.toLowerCase()) {
            case "roundrobin" -> (SelectionStrategy<T>) roundRobin();
            case "least"      -> (SelectionStrategy<T>) leastConnection();
            case "hash"       -> hash(keyExtractor);
            default -> throw new IllegalArgumentException(
                    "Unknown strategy: " + strategy
            );
        };
    }

    /**
     * Overload of {@link #create(String, Function)} that additionally supports
     * {@code "adaptive"}.
     *
     * <p>When {@code strategy} is {@code "adaptive"} and {@code registry} is
     * non-null, returns an {@link AdaptiveStrategy} for the given channel.
     * If {@code registry} is {@code null} (e.g. gRPC server not wired), falls
     * back to round-robin so the system stays operational.
     *
     * @param strategy     strategy identifier
     * @param channelName  channel name (required for {@code "adaptive"})
     * @param registry     AI weight registry (required for {@code "adaptive"})
     * @param keyExtractor function to extract hash key (required for {@code "hash"})
     * @param <T> candidate type
     * @return selection strategy instance
     * @throws IllegalArgumentException if strategy is unknown
     */
    @SuppressWarnings("unchecked")
    public static <T> SelectionStrategy<T> create(String strategy,
                                                   String channelName,
                                                   AiWeightRegistry registry,
                                                   Function<MessageContext, String> keyExtractor) {
        if ("adaptive".equalsIgnoreCase(strategy)) {
            if (registry != null) {
                return (SelectionStrategy<T>) adaptive(channelName, registry);
            }
            return (SelectionStrategy<T>) roundRobin();
        }
        return create(strategy, keyExtractor);
    }
}