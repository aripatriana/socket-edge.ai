package id.co.jalin.seconsole.engine;

import id.co.jalin.seconsole.engine.dto.ChannelSummary;
import id.co.jalin.seconsole.engine.dto.SocketSummary;
import id.co.jalin.seconsole.engine.model.ChannelCfg;
import id.co.jalin.seconsole.engine.model.ChannelSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reshapes a flat {@link ChannelSnapshot} (HTTP path) into the console's
 * {@link ChannelSummary} list. p99 fields are 0 on this path — the HTTP
 * engine API does not expose them. Use {@link GrpcChannelSnapshotMapper}
 * for the gRPC path which carries full StatSummary (avg/min/max/p90/p95/p99).
 */
public final class ChannelSnapshotMapper {

    private ChannelSnapshotMapper() {}

    public static List<ChannelSummary> toChannelList(
            ChannelSnapshot snap,
            Map<String, ChannelCfg> configByName) {

        if (snap == null || snap.sockets() == null || snap.sockets().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<SocketSummary>> byChannel = new LinkedHashMap<>();
        for (ChannelSnapshot.Socket s : snap.sockets()) {
            if (s == null || s.name() == null) continue;
            byChannel.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(toSocketSummary(s));
        }

        List<ChannelSummary> out = new ArrayList<>(byChannel.size());
        for (Map.Entry<String, List<SocketSummary>> e : byChannel.entrySet()) {
            out.add(buildChannel(e.getKey(), e.getValue(),
                    configByName == null ? null : configByName.get(e.getKey())));
        }
        return Collections.unmodifiableList(out);
    }

    // ── HTTP model → SocketSummary ────────────────────────────────────────────

    private static SocketSummary toSocketSummary(ChannelSnapshot.Socket s) {
        return new SocketSummary(
                s.hashId(), s.socketId(), s.name(), s.type(),
                toRuntime(s.runtime()),
                toQueue(s.queue()),
                toMetrics(s.metrics())
        );
    }

    private static SocketSummary.Runtime toRuntime(ChannelSnapshot.Runtime r) {
        if (r == null) return new SocketSummary.Runtime("DOWN", "-", "-", 0, 0L, 0L, 0L);
        return new SocketSummary.Runtime(
                r.state() == null ? "DOWN" : r.state(),
                r.localHost() == null ? "-" : r.localHost(),
                r.remoteHost() == null ? "-" : r.remoteHost(),
                r.activeChannels(), r.startTime(), r.lastConnect(), r.lastDisconnect()
        );
    }

    private static SocketSummary.Queue toQueue(ChannelSnapshot.Queue q) {
        if (q == null) return new SocketSummary.Queue(0, 0, 0, 0, 0, 0);
        return new SocketSummary.Queue(q.msgIn(), q.msgOut(), q.depth(),
                q.errCount(), q.lastErr(), q.lastMsg());
    }

    private static SocketSummary.Metrics toMetrics(ChannelSnapshot.Metrics m) {
        if (m == null) {
            return new SocketSummary.Metrics(
                    new SocketSummary.Latency(0, 0, 0, 0, 0, 0),
                    new SocketSummary.Tps(0, 0, 0, 0, 0, 0),
                    new SocketSummary.Tps(0, 0, 0, 0, 0, 0)
            );
        }
        return new SocketSummary.Metrics(
                toLatency(m.latency()),
                toTps(m.pressureTps()),
                toTps(m.throughputTps())
        );
    }

    private static SocketSummary.Latency toLatency(ChannelSnapshot.Latency l) {
        if (l == null) return new SocketSummary.Latency(0, 0, 0, 0, 0, 0);
        return new SocketSummary.Latency(l.avgNs(), l.minNs(), l.maxNs(), l.p90Ns(), l.p95Ns(), 0);
    }

    private static SocketSummary.Tps toTps(ChannelSnapshot.Tps t) {
        if (t == null) return new SocketSummary.Tps(0, 0, 0, 0, 0, 0);
        return new SocketSummary.Tps(t.avg(), t.min(), t.max(), t.p90(), t.p95(), 0);
    }

    // ── Aggregation (shared with GrpcChannelSnapshotMapper) ──────────────────

    static ChannelSummary buildChannel(String name, List<SocketSummary> sockets, ChannelCfg cfg) {
        List<SocketSummary> servers = new ArrayList<>();
        List<SocketSummary> clients = new ArrayList<>();
        for (SocketSummary s : sockets) {
            if ("SERVER".equalsIgnoreCase(s.type())) servers.add(s);
            else clients.add(s);
        }

        int up = 0;
        boolean anyError = false, allHealthy = !sockets.isEmpty(), allDownOrStandby = true;

        long totalPressureAvg = 0, maxPressureAvg = 0, maxPressureP95 = 0, maxPressureP99 = 0;
        long totalThroughputAvg = 0, maxThroughputAvg = 0, maxThroughputP95 = 0, maxThroughputP99 = 0;
        long maxLatencyAvg = 0, maxLatencyMax = 0, maxLatencyP95 = 0, maxLatencyP99 = 0;
        long totalMsgIn = 0, totalMsgOut = 0, totalInFlight = 0, totalErrCnt = 0, maxLastErr = 0;

        for (SocketSummary s : sockets) {
            String st = s.runtime().state() == null ? "" : s.runtime().state().toUpperCase();
            switch (st) {
                case "ACTIVE", "LISTEN" -> up++;
                case "ERROR" -> anyError = true;
            }
            boolean healthy = "ACTIVE".equals(st) || "LISTEN".equals(st);
            if (!healthy) allHealthy = false;
            if (!"DOWN".equals(st) && !"STANDBY".equals(st) && !st.isEmpty()) allDownOrStandby = false;

            SocketSummary.Tps p = s.metrics().pressureTps();
            totalPressureAvg += p.avg();
            maxPressureAvg    = Math.max(maxPressureAvg, p.avg());
            maxPressureP95    = Math.max(maxPressureP95, p.p95());
            maxPressureP99    = Math.max(maxPressureP99, p.p99());

            SocketSummary.Tps t = s.metrics().throughputTps();
            totalThroughputAvg += t.avg();
            maxThroughputAvg    = Math.max(maxThroughputAvg, t.avg());
            maxThroughputP95    = Math.max(maxThroughputP95, t.p95());
            maxThroughputP99    = Math.max(maxThroughputP99, t.p99());

            SocketSummary.Latency lat = s.metrics().latency();
            maxLatencyAvg = Math.max(maxLatencyAvg, lat.avgNs());
            maxLatencyMax = Math.max(maxLatencyMax, lat.maxNs());
            maxLatencyP95 = Math.max(maxLatencyP95, lat.p95Ns());
            maxLatencyP99 = Math.max(maxLatencyP99, lat.p99Ns());

            SocketSummary.Queue q = s.queue();
            totalMsgIn    += q.msgIn();
            totalMsgOut   += q.msgOut();
            totalInFlight += q.depth();
            totalErrCnt   += q.errCount();
            maxLastErr     = Math.max(maxLastErr, q.lastErr());
        }

        String aggregateState;
        if (sockets.isEmpty())     aggregateState = "UNKNOWN";
        else if (anyError)         aggregateState = "ERROR";
        else if (allHealthy)       aggregateState = "ACTIVE";
        else if (allDownOrStandby) aggregateState = "DOWN";
        else                       aggregateState = "DEGRADED";

        Integer listenPort = null;
        String clientStrategy = null;
        if (cfg != null) {
            if (cfg.server() != null) listenPort = cfg.server().listenPort();
            if (cfg.client() != null) clientStrategy = cfg.client().strategy();
        }

        return new ChannelSummary(
                name, aggregateState, up, sockets.size(),
                new ChannelSummary.Aggregate(
                        new ChannelSummary.Latency(maxLatencyAvg, maxLatencyMax, maxLatencyP95, maxLatencyP99),
                        new ChannelSummary.PressureTps(totalPressureAvg, maxPressureAvg, maxPressureP95, maxPressureP99),
                        new ChannelSummary.ThroughputTps(totalThroughputAvg, maxThroughputAvg, maxThroughputP95, maxThroughputP99),
                        totalMsgIn, totalMsgOut, totalInFlight, totalErrCnt, maxLastErr
                ),
                listenPort, clientStrategy,
                servers, clients
        );
    }
}
