package com.socket.edge.ai.engine;

import java.util.Map;

/**
 * Abstraction over weight publication — decouples BanditEngine from gRPC transport.
 * WeightPublisher (gRPC) implements this in production; tests supply lightweight stubs.
 */
@FunctionalInterface
public interface Publisher {
    void publish(String channelName, Map<String, Integer> weights, double reward, double confidence);
}
