package com.socket.edge.ai.config;

/**
 * Runtime configuration loaded from system properties.
 *
 * Usage:
 *   java -Dse.core.host=10.0.0.1 -Dse.core.port=9090 -Dse.ai.alpha=0.5 -jar se-ai.jar
 */
public record AiConfig(
        String coreHost,
        int    corePort,
        double alpha,
        int    minWeight,
        int    maxWeight
) {

    public static AiConfig fromSystem() {
        return new AiConfig(
                System.getProperty("se.core.host", "localhost"),
                Integer.parseInt(System.getProperty("se.core.port", "9090")),
                Double.parseDouble(System.getProperty("se.ai.alpha", "0.5")),
                Integer.parseInt(System.getProperty("se.ai.min.weight", "5")),
                Integer.parseInt(System.getProperty("se.ai.max.weight", "80"))
        );
    }
}
