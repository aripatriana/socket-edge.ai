package com.socket.edge.ai;

import com.socket.edge.ai.bandit.SoftmaxWeightSelector;
import com.socket.edge.ai.config.AiConfig;
import com.socket.edge.ai.engine.BanditEngine;
import com.socket.edge.ai.feature.FeatureBuilder;
import com.socket.edge.ai.grpc.MetricSubscriber;
import com.socket.edge.ai.grpc.WeightPublisher;
import com.socket.edge.ai.reward.RewardCalculator;
import com.socket.edge.ai.safety.SafetyConstraint;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * SE-AI entry point.
 *
 * Starts a gRPC client connection to se-core, subscribes to the metrics stream,
 * and runs the LinUCB bandit engine in a loop until the process is stopped.
 *
 * Configuration via system properties (see AiConfig):
 *   -Dse.core.host=localhost
 *   -Dse.core.port=9090
 *   -Dse.ai.alpha=0.5
 *   -Dse.ai.min.weight=5
 *   -Dse.ai.max.weight=80
 */
public class SeAiApplication {

    private static final Logger log = LoggerFactory.getLogger(SeAiApplication.class);

    public static void main(String[] args) throws InterruptedException {
        AiConfig config = AiConfig.fromSystem();
        log.info("SE-AI starting: core={}:{} alpha={} weight=[{},{}]",
                config.coreHost(), config.corePort(),
                config.alpha(), config.minWeight(), config.maxWeight());

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(config.coreHost(), config.corePort())
                .usePlaintext()
                .build();

        WeightPublisher   publisher = new WeightPublisher(channel);
        BanditEngine      engine    = new BanditEngine(
                new FeatureBuilder(),
                new SoftmaxWeightSelector(),
                new RewardCalculator(),
                new SafetyConstraint(config.minWeight(), config.maxWeight()),
                publisher,
                config.alpha()
        );
        MetricSubscriber subscriber = new MetricSubscriber(channel, engine);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("SE-AI shutting down");
            subscriber.stop();
            channel.shutdown();
            try {
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        subscriber.start(); // blocks until stopped
    }
}
