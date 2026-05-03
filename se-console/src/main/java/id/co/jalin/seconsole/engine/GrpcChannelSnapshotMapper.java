package id.co.jalin.seconsole.engine;

import com.socket.edge.grpc.ChannelSnapshot;
import com.socket.edge.grpc.SocketMetrics;
import com.socket.edge.grpc.SocketQueue;
import com.socket.edge.grpc.SocketRuntime;
import com.socket.edge.grpc.SocketSnapshot;
import com.socket.edge.grpc.StatSummary;
import id.co.jalin.seconsole.engine.dto.ChannelSummary;
import id.co.jalin.seconsole.engine.dto.SocketSummary;
import id.co.jalin.seconsole.engine.model.ChannelCfg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a gRPC {@link ChannelSnapshot} proto to the console's
 * {@link ChannelSummary} list, preserving full StatSummary
 * (avg/min/max/p90/p95/p99) that the HTTP path cannot provide.
 *
 * Aggregation semantics mirror {@link ChannelSnapshotMapper#buildChannel}.
 */
public final class GrpcChannelSnapshotMapper {

    private GrpcChannelSnapshotMapper() {}

    public static List<ChannelSummary> toChannelList(
            ChannelSnapshot proto,
            Map<String, ChannelCfg> configByName) {

        if (proto == null || proto.getSocketsList().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<SocketSummary>> byChannel = new LinkedHashMap<>();
        for (SocketSnapshot s : proto.getSocketsList()) {
            if (s.getName().isBlank()) continue;
            byChannel.computeIfAbsent(s.getName(), k -> new ArrayList<>())
                     .add(toSocketSummary(s));
        }

        List<ChannelSummary> out = new ArrayList<>(byChannel.size());
        for (Map.Entry<String, List<SocketSummary>> e : byChannel.entrySet()) {
            out.add(ChannelSnapshotMapper.buildChannel(
                    e.getKey(), e.getValue(),
                    configByName == null ? null : configByName.get(e.getKey())));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Convert a gRPC {@link ChannelSnapshot} to the HTTP wire model
     * {@link id.co.jalin.seconsole.engine.model.ChannelSnapshot} for
     * history recording. p99 is dropped — the history schema predates it.
     */
    public static id.co.jalin.seconsole.engine.model.ChannelSnapshot toHttpModel(
            ChannelSnapshot proto) {

        List<id.co.jalin.seconsole.engine.model.ChannelSnapshot.Socket> sockets =
                proto.getSocketsList().stream()
                        .map(GrpcChannelSnapshotMapper::toHttpSocket)
                        .toList();

        com.socket.edge.grpc.ChannelAggregate a = proto.getAggregate();
        id.co.jalin.seconsole.engine.model.ChannelSnapshot.Aggregate agg =
                new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Aggregate(
                        a.getSocketCount(), a.getSocketsUp(), a.getSocketsDown(),
                        a.getTotalActiveChannels(), a.getTotalMsgIn(), a.getTotalMsgOut(),
                        a.getTotalQueueDepth(), a.getTotalErrCount(), 0L, 0L);

        return new id.co.jalin.seconsole.engine.model.ChannelSnapshot(
                proto.getHeader().getSnapshotId(),
                proto.getHeader().getCapturedAt(),
                proto.getHeader().getCaptureDuration(),
                sockets, agg);
    }

    // ── proto → SocketSummary (with p99) ─────────────────────────────────────

    private static SocketSummary toSocketSummary(SocketSnapshot s) {
        return new SocketSummary(
                s.getHashId(), s.getSocketId(), s.getName(), s.getType(),
                toRuntime(s.getRuntime()),
                toQueue(s.getQueue()),
                toMetrics(s.getMetrics())
        );
    }

    private static SocketSummary.Runtime toRuntime(SocketRuntime r) {
        if (r == null) return new SocketSummary.Runtime("DOWN", "-", "-", 0, 0L, 0L, 0L);
        return new SocketSummary.Runtime(
                r.getState().isBlank() ? "DOWN" : r.getState(),
                r.getLocalHost().isBlank() ? "-" : r.getLocalHost(),
                r.getRemoteHost().isBlank() ? "-" : r.getRemoteHost(),
                r.getActiveChannels(), r.getStartTime(), r.getLastConnect(), r.getLastDisconnect()
        );
    }

    private static SocketSummary.Queue toQueue(SocketQueue q) {
        if (q == null) return new SocketSummary.Queue(0, 0, 0, 0, 0, 0);
        return new SocketSummary.Queue(
                q.getMsgIn(), q.getMsgOut(), q.getDepth(),
                q.getErrCount(), q.getLastErr(), q.getLastMsg()
        );
    }

    private static SocketSummary.Metrics toMetrics(SocketMetrics m) {
        if (m == null) {
            return new SocketSummary.Metrics(
                    new SocketSummary.Stat(0, 0, 0, 0, 0, 0),
                    new SocketSummary.Stat(0, 0, 0, 0, 0, 0),
                    new SocketSummary.Stat(0, 0, 0, 0, 0, 0)
            );
        }
        return new SocketSummary.Metrics(
                toStat(m.getLatencyNs()),
                toStat(m.getPressureTps()),
                toStat(m.getThroughputTps())
        );
    }

    private static SocketSummary.Stat toStat(StatSummary s) {
        if (s == null) return new SocketSummary.Stat(0, 0, 0, 0, 0, 0);
        return new SocketSummary.Stat(s.getAvg(), s.getMin(), s.getMax(),
                s.getP90(), s.getP95(), s.getP99());
    }

    // ── proto → HTTP wire model (for history recording, p99 dropped) ──────────

    private static id.co.jalin.seconsole.engine.model.ChannelSnapshot.Socket toHttpSocket(
            SocketSnapshot s) {
        return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Socket(
                s.getHashId(), s.getSocketId(), s.getName(), s.getType(),
                toHttpRuntime(s.getRuntime()),
                toHttpQueue(s.getQueue()),
                toHttpMetrics(s.getMetrics())
        );
    }

    private static id.co.jalin.seconsole.engine.model.ChannelSnapshot.Runtime toHttpRuntime(
            SocketRuntime r) {
        if (r == null) return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Runtime(
                "DOWN", "-", "-", 0, 0L, 0L, 0L);
        return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Runtime(
                r.getState(), r.getLocalHost(), r.getRemoteHost(),
                r.getActiveChannels(), r.getStartTime(), r.getLastConnect(), r.getLastDisconnect());
    }

    private static id.co.jalin.seconsole.engine.model.ChannelSnapshot.Queue toHttpQueue(
            SocketQueue q) {
        if (q == null) return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Queue(
                0, 0, 0, 0, 0, 0);
        return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Queue(
                q.getMsgIn(), q.getMsgOut(), q.getDepth(),
                q.getErrCount(), q.getLastErr(), q.getLastMsg());
    }

    private static id.co.jalin.seconsole.engine.model.ChannelSnapshot.Metrics toHttpMetrics(
            SocketMetrics m) {
        if (m == null) return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Metrics(
                null, null, null);
        return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Metrics(
                toHttpLatency(m.getLatencyNs()),
                toHttpTps(m.getPressureTps()),
                toHttpTps(m.getThroughputTps()));
    }

    private static id.co.jalin.seconsole.engine.model.ChannelSnapshot.Latency toHttpLatency(
            StatSummary s) {
        if (s == null) return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Latency(
                0, 0, 0, 0, 0);
        return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Latency(
                s.getAvg(), s.getMin(), s.getMax(), s.getP90(), s.getP95());
    }

    private static id.co.jalin.seconsole.engine.model.ChannelSnapshot.Tps toHttpTps(StatSummary s) {
        if (s == null) return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Tps(
                0, 0, 0, 0, 0);
        return new id.co.jalin.seconsole.engine.model.ChannelSnapshot.Tps(
                s.getAvg(), s.getMin(), s.getMax(), s.getP90(), s.getP95());
    }
}
