package com.socket.edge.ai.grpc;

import com.socket.edge.grpc.*;
import com.socket.edge.ai.engine.BanditEngine;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Subscribes to se-core's SubscribeMetrics server-streaming RPC.
 *
 * On each MetricsBundle received, delegates to BanditEngine for processing.
 * Reconnects automatically on stream error or completion with exponential
 * backoff (1s → 2s → 4s → … → 60s cap).
 */
public class MetricSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MetricSubscriber.class);
    private static final long MAX_BACKOFF_MS = 60_000;

    private final ManagedChannel channel;
    private final BanditEngine   engine;
    private volatile boolean     running = true;

    public MetricSubscriber(ManagedChannel channel, BanditEngine engine) {
        this.channel = channel;
        this.engine  = engine;
    }

    /**
     * Starts the subscription loop in the calling thread (blocks until stopped).
     * Reconnects automatically on failure.
     */
    public void start() {
        long backoffMs = 1000;

        while (running) {
            CountDownLatch done = new CountDownLatch(1);
            CoreServiceGrpc.CoreServiceStub stub = CoreServiceGrpc.newStub(channel);

            stub.subscribeMetrics(Empty.newBuilder().build(), new StreamObserver<MetricsBundle>() {
                @Override
                public void onNext(MetricsBundle bundle) {
                    try {
                        engine.process(bundle);
                    } catch (Exception ex) {
                        log.error("Error processing MetricsBundle", ex);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    log.warn("Metrics stream error: {}", t.getMessage());
                    done.countDown();
                }

                @Override
                public void onCompleted() {
                    log.info("Metrics stream completed — reconnecting");
                    done.countDown();
                }
            });

            log.info("Subscribed to se-core metrics stream");
            backoffMs = 1000; // reset on successful connection

            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (!running) break;

            log.info("Reconnecting in {}ms…", backoffMs);
            try {
                TimeUnit.MILLISECONDS.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
        }
    }

    public void stop() {
        running = false;
    }
}
