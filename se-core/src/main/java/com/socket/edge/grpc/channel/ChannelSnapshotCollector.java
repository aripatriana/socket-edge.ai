package com.socket.edge.grpc.channel;

import com.socket.edge.core.SocketTelemetry;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.grpc.*;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.Queue;
import com.socket.edge.model.RuntimeState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds a {@link ChannelSnapshot} proto from live {@link TelemetryRegistry} data.
 *
 * Called once per broadcast interval by MetricsBroadcaster. All reads are
 * non-blocking — TelemetryRegistry exposes atomic snapshots via getMetrics(),
 * getRuntimeState(), and getQueue() on each SocketTelemetry entry.
 */
public class ChannelSnapshotCollector {

    private final TelemetryRegistry registry;

    public ChannelSnapshotCollector(TelemetryRegistry registry) {
        this.registry = registry;
    }

    public ChannelSnapshot collect(String snapshotId) {
        long capturedAt = System.currentTimeMillis();

        Collection<SocketTelemetry> all = registry.getAllTelemetry();

        List<SocketSnapshot> socketSnapshots = new ArrayList<>(all.size());

        int socketsUp = 0, socketsDown = 0, totalActiveChannels = 0;
        long totalMsgIn = 0, totalMsgOut = 0, totalQueueDepth = 0, totalErrCount = 0;

        for (SocketTelemetry t : all) {
            Metrics     m  = t.getMetrics();
            RuntimeState rs = t.getRuntimeState();
            Queue       q  = t.getQueue();

            boolean up = isUp(rs.status());
            if (up) socketsUp++; else socketsDown++;
            totalActiveChannels += rs.active();
            totalMsgIn          += q.msgIn();
            totalMsgOut         += q.msgOut();
            totalQueueDepth     += q.queue();
            totalErrCount       += q.errCnt();

            socketSnapshots.add(SocketSnapshot.newBuilder()
                    .setBindingId(m.bindingId())
                    .setSocketId(m.socketId())
                    .setName(m.name())
                    .setType(m.type())
                    .setRuntime(buildRuntime(rs))
                    .setQueue(buildQueue(q))
                    .setMetrics(buildMetrics(m))
                    .build());
        }

        int captureDurationMs = (int) (System.currentTimeMillis() - capturedAt);

        return ChannelSnapshot.newBuilder()
                .setHeader(SnapshotHeader.newBuilder()
                        .setSnapshotId(snapshotId)
                        .setCapturedAt(capturedAt)
                        .setCaptureDuration(captureDurationMs)
                        .build())
                .addAllSockets(socketSnapshots)
                .setAggregate(ChannelAggregate.newBuilder()
                        .setSocketCount(all.size())
                        .setSocketsUp(socketsUp)
                        .setSocketsDown(socketsDown)
                        .setTotalActiveChannels(totalActiveChannels)
                        .setTotalMsgIn(totalMsgIn)
                        .setTotalMsgOut(totalMsgOut)
                        .setTotalQueueDepth(totalQueueDepth)
                        .setTotalErrCount(totalErrCount)
                        .build())
                .build();
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private static SocketRuntime buildRuntime(RuntimeState rs) {
        return SocketRuntime.newBuilder()
                .setState(rs.status())
                .setLocalHost(rs.localHost())
                .setRemoteHost(rs.remoteHost())
                .setActiveChannels(rs.active())
                .setStartTime(rs.startTime())
                .setLastConnect(rs.lastConnect())
                .setLastDisconnect(rs.lastDisconnect())
                .build();
    }

    private static SocketQueue buildQueue(Queue q) {
        return SocketQueue.newBuilder()
                .setMsgIn(q.msgIn())
                .setMsgOut(q.msgOut())
                .setDepth(q.queue())
                .setErrCount(q.errCnt())
                .setLastErr(q.lastErr())
                .setLastMsg(q.lastMsg())
                .build();
    }

    private static SocketMetrics buildMetrics(Metrics m) {
        return SocketMetrics.newBuilder()
                .setLatencyNs(StatSummary.newBuilder()
                        .setAvg(m.avgLatency())
                        .setMin(m.minLatency())
                        .setMax(m.maxLatency())
                        .setP90(m.latencyP90Ns())
                        .setP95(m.latencyP95Ns())
                        .setP99(m.latencyP99Ns())
                        .build())
                .setPressureTps(StatSummary.newBuilder()
                        .setAvg(m.pressureTps())
                        .setMin(m.minPressureTps())
                        .setMax(m.maxPressureTps())
                        .setP90(m.pressureTpsP90())
                        .setP95(m.pressureTpsP95())
                        .setP99(m.pressureTpsP99())
                        .build())
                .setThroughputTps(StatSummary.newBuilder()
                        .setAvg(m.throughputTps())
                        .setMin(m.minThroughputTps())
                        .setMax(m.maxThroughputTps())
                        .setP90(m.throughputTpsP90())
                        .setP95(m.throughputTpsP95())
                        .setP99(m.throughputTpsP99())
                        .build())
                .build();
    }

    private static boolean isUp(String state) {
        return state != null
                && !state.equals("DOWN")
                && !state.equals("ERROR");
    }
}
