package com.socket.edge.grpc;

import com.socket.edge.grpc.channel.ChannelSnapshotCollector;
import com.socket.edge.grpc.jvm.JvmMetricsCollector;
import com.socket.edge.grpc.os.OsMetricsCollector;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Schedules periodic metric collection and fans out MetricsBundle
 * to all active gRPC streaming subscribers (se-console, se-ai).
 *
 * Thread model: single scheduled thread for collection; CopyOnWriteArrayList
 * for subscriber list so add/remove during broadcast is safe.
 */
public class MetricsBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MetricsBroadcaster.class);

    private final OsMetricsCollector              osCollector;
    private final JvmMetricsCollector             jvmCollector;
    private final ChannelSnapshotCollector        channelCollector;
    private final long                            intervalMs;
    private final String                          nodeId;
    private final CopyOnWriteArrayList<StreamObserver<MetricsBundle>> subscribers
            = new CopyOnWriteArrayList<>();
    private final AtomicLong                      frameSeq = new AtomicLong(0);
    private       ScheduledExecutorService        scheduler;

    public MetricsBroadcaster(OsMetricsCollector osCollector,
                               JvmMetricsCollector jvmCollector,
                               ChannelSnapshotCollector channelCollector,
                               long intervalMs, String nodeId) {
        this.osCollector      = osCollector;
        this.jvmCollector     = jvmCollector;
        this.channelCollector = channelCollector;
        this.intervalMs       = intervalMs;
        this.nodeId           = nodeId;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "grpc-metrics-broadcaster");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::broadcast, 0, intervalMs, TimeUnit.MILLISECONDS);
        log.info("MetricsBroadcaster started: interval={}ms node={}", intervalMs, nodeId);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (StreamObserver<MetricsBundle> obs : subscribers) {
            try { obs.onCompleted(); } catch (Exception ignored) {}
        }
        subscribers.clear();
        log.info("MetricsBroadcaster stopped");
    }

    public void addSubscriber(StreamObserver<MetricsBundle> observer) {
        subscribers.add(observer);
        log.info("Metrics subscriber added, total={}", subscribers.size());
    }

    public void removeSubscriber(StreamObserver<MetricsBundle> observer) {
        subscribers.remove(observer);
        log.info("Metrics subscriber removed, total={}", subscribers.size());
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void broadcast() {
        if (subscribers.isEmpty()) return;

        try {
            String snapshotId = String.format("%016X", frameSeq.incrementAndGet());

            OsSnapshot      os      = osCollector.collect(snapshotId);
            JvmSnapshot     jvm     = jvmCollector.collect(snapshotId);
            ChannelSnapshot channel = channelCollector.collect(snapshotId);

            MetricsBundle bundle = MetricsBundle.newBuilder()
                    .setOs(os)
                    .setJvm(jvm)
                    .setChannel(channel)
                    .setNodeId(nodeId)
                    .build();

            for (StreamObserver<MetricsBundle> obs : subscribers) {
                try {
                    obs.onNext(bundle);
                } catch (Exception e) {
                    log.warn("Failed to send metrics to subscriber, removing: {}", e.getMessage());
                    removeSubscriber(obs);
                }
            }
        } catch (Exception e) {
            log.error("Metrics collection error", e);
        }
    }
}
