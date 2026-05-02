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
 * Reshapes a flat {@link ChannelSnapshot} from SE-Core into the console's
 * {@link ChannelSummary} list — grouped by {@code name} with per-channel
 * aggregates already computed.
 *
 * <p>This class is the single seam between the engine wire format and
 * console-facing DTOs. When either side evolves, this is the only file
 * that needs updating.
 *
 * <p>Aggregate semantics:
 * <ul>
 *   <li><b>Latency</b> — worst-of (max) across sockets. One hot socket
 *       is the actionable signal; averages across healthy and unhealthy
 *       sockets hide the outlier.</li>
 *   <li><b>Pressure / Throughput</b> — we emit both {@code totalAvg} (sum
 *       of per-socket avg) and {@code maxAvg} (max of per-socket avg),
 *       because the channels list UI wants total and the detail UI wants
 *       the hot-socket signal.</li>
 *   <li><b>Counters</b> — sum across sockets.</li>
 *   <li><b>errLast</b> — max (latest) across sockets.</li>
 * </ul>
 */
public final class ChannelSnapshotMapper {

    private ChannelSnapshotMapper() {}

    /**
     * Group by channel name, preserving input order, and compute per-channel
     * aggregates. Config data (listen port, client strategy) is merged in by
     * name from the separate {@code /config/channels} poll.
     */
    public static List<ChannelSummary> toChannelList(
            ChannelSnapshot snap,
            Map<String, ChannelCfg> configByName) {

        if (snap == null || snap.sockets() == null || snap.sockets().isEmpty()) {
            return Collections.emptyList();
        }

        // LinkedHashMap preserves first-seen order so the UI listing is stable.
        Map<String, List<SocketSummary>> byChannel = new LinkedHashMap<>();
        for (ChannelSnapshot.Socket s : snap.sockets()) {
            if (s == null || s.name() == null) continue;
            SocketSummary ss = toSocketSummary(s);
            byChannel.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(ss);
        }

        List<ChannelSummary> out = new ArrayList<>(byChannel.size());
        for (Map.Entry<String, List<SocketSummary>> e : byChannel.entrySet()) {
            out.add(buildChannel(e.getKey(), e.getValue(),
                    configByName == null ? null : configByName.get(e.getKey())));
        }
        return Collections.unmodifiableList(out);
    }

    // -------------------------------------------------------------------------

    private static SocketSummary toSocketSummary(ChannelSnapshot.Socket s) {
        return new SocketSummary(
                s.hashId(),
                s.socketId(),
                s.name(),
                s.type(),
                toRuntime(s.runtime()),
                toQueue(s.queue()),
                toMetrics(s.metrics())
        );
    }

    private static SocketSummary.Runtime toRuntime(ChannelSnapshot.Runtime r) {
        if (r == null) {
            return new SocketSummary.Runtime("DOWN", "-", "-", 0, 0L, 0L, 0L);
        }
        return new SocketSummary.Runtime(
                r.state() == null ? "DOWN" : r.state(),
                r.localHost() == null ? "-" : r.localHost(),
                r.remoteHost() == null ? "-" : r.remoteHost(),
                r.activeChannels(),
                r.startTime(),
                r.lastConnect(),
                r.lastDisconnect()
        );
    }

    private static SocketSummary.Queue toQueue(ChannelSnapshot.Queue q) {
        if (q == null) return new SocketSummary.Queue(0, 0, 0, 0, 0, 0);
        return new SocketSummary.Queue(
                q.msgIn(), q.msgOut(), q.depth(),
                q.errCount(), q.lastErr(), q.lastMsg()
        );
    }

    private static SocketSummary.Metrics toMetrics(ChannelSnapshot.Metrics m) {
        if (m == null) {
            return new SocketSummary.Metrics(
                    new SocketSummary.Latency(0, 0, 0, 0, 0),
                    new SocketSummary.Tps(0, 0, 0, 0, 0),
                    new SocketSummary.Tps(0, 0, 0, 0, 0)
            );
        }
        return new SocketSummary.Metrics(
                toLatency(m.latency()),
                toTps(m.pressureTps()),
                toTps(m.throughputTps())
        );
    }

    private static SocketSummary.Latency toLatency(ChannelSnapshot.Latency l) {
        if (l == null) return new SocketSummary.Latency(0, 0, 0, 0, 0);
        return new SocketSummary.Latency(l.avgNs(), l.minNs(), l.maxNs(), l.p90Ns(), l.p95Ns());
    }

    private static SocketSummary.Tps toTps(ChannelSnapshot.Tps t) {
        if (t == null) return new SocketSummary.Tps(0, 0, 0, 0, 0);
        return new SocketSummary.Tps(t.avg(), t.min(), t.max(), t.p90(), t.p95());
    }

    // -------------------------------------------------------------------------

    /**
     * Build one channel from its socket list plus optional config. Computes
     * aggregate state and all per-channel aggregate metrics.
     */
    private static ChannelSummary buildChannel(
            String name,
            List<SocketSummary> sockets,
            ChannelCfg cfg) {

        List<SocketSummary> servers = new ArrayList<>();
        List<SocketSummary> clients = new ArrayList<>();
        for (SocketSummary s : sockets) {
            if ("SERVER".equalsIgnoreCase(s.type())) servers.add(s);
            else clients.add(s);
        }

        // Aggregate state — unchanged logic from pre-rewrite.
        int up = 0;
        boolean anyError = false;
        boolean allHealthy = !sockets.isEmpty();
        boolean allDownOrStandby = true;

        // Metric accumulators
        long totalPressureAvg = 0, maxPressureAvg = 0, maxPressureP95 = 0;
        long totalThroughputAvg = 0, maxThroughputAvg = 0, maxThroughputP95 = 0;
        long maxLatencyAvg = 0, maxLatencyMax = 0, maxLatencyP95 = 0;
        long totalMsgIn = 0, totalMsgOut = 0;
        long totalInFlight = 0;
        long totalErrCnt = 0, maxLastErr = 0;

        for (SocketSummary s : sockets) {
            String st = s.runtime().state() == null ? "" : s.runtime().state().toUpperCase();
            switch (st) {
                case "ACTIVE", "LISTEN" -> up++;
                case "ERROR" -> anyError = true;
            }
            boolean healthy = "ACTIVE".equals(st) || "LISTEN".equals(st);
            if (!healthy) allHealthy = false;
            if (!"DOWN".equals(st) && !"STANDBY".equals(st) && !st.isEmpty()) allDownOrStandby = false;

            // pressure
            SocketSummary.Tps p = s.metrics().pressureTps();
            totalPressureAvg += p.avg();
            maxPressureAvg   = Math.max(maxPressureAvg, p.avg());
            maxPressureP95   = Math.max(maxPressureP95, p.p95());

            // throughput
            SocketSummary.Tps t = s.metrics().throughputTps();
            totalThroughputAvg += t.avg();
            maxThroughputAvg   = Math.max(maxThroughputAvg, t.avg());
            maxThroughputP95   = Math.max(maxThroughputP95, t.p95());

            // latency — worst-of
            SocketSummary.Latency lat = s.metrics().latency();
            maxLatencyAvg = Math.max(maxLatencyAvg, lat.avgNs());
            maxLatencyMax = Math.max(maxLatencyMax, lat.maxNs());
            maxLatencyP95 = Math.max(maxLatencyP95, lat.p95Ns());

            // queue / counters
            SocketSummary.Queue q = s.queue();
            totalMsgIn    += q.msgIn();
            totalMsgOut   += q.msgOut();
            totalInFlight += q.depth();
            totalErrCnt   += q.errCount();
            maxLastErr     = Math.max(maxLastErr, q.lastErr());
        }

        String aggregateState;
        if (sockets.isEmpty())       aggregateState = "UNKNOWN";
        else if (anyError)           aggregateState = "ERROR";
        else if (allHealthy)         aggregateState = "ACTIVE";
        else if (allDownOrStandby)   aggregateState = "DOWN";
        else                         aggregateState = "DEGRADED";

        Integer listenPort = null;
        String clientStrategy = null;
        if (cfg != null) {
            if (cfg.server() != null) listenPort = cfg.server().listenPort();
            if (cfg.client() != null) clientStrategy = cfg.client().strategy();
        }

        ChannelSummary.Aggregate aggregate = new ChannelSummary.Aggregate(
                new ChannelSummary.Latency(maxLatencyAvg, maxLatencyMax, maxLatencyP95),
                new ChannelSummary.PressureTps(totalPressureAvg, maxPressureAvg, maxPressureP95),
                new ChannelSummary.ThroughputTps(totalThroughputAvg, maxThroughputAvg, maxThroughputP95),
                totalMsgIn, totalMsgOut,
                totalInFlight,
                totalErrCnt, maxLastErr
        );

        return new ChannelSummary(
                name, aggregateState,
                up, sockets.size(),
                aggregate,
                listenPort, clientStrategy,
                servers, clients
        );
    }
}
