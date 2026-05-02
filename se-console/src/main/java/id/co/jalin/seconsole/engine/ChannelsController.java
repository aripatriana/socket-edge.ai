package id.co.jalin.seconsole.engine;

import id.co.jalin.seconsole.engine.EngineChannelSnapshotService.Snapshot;
import id.co.jalin.seconsole.engine.dto.ChannelHistoryResponse;
import id.co.jalin.seconsole.engine.dto.ChannelHistoryResponse.EndpointRef;
import id.co.jalin.seconsole.engine.dto.ChannelHistoryResponse.HistorySample;
import id.co.jalin.seconsole.engine.dto.ChannelSummary;
import id.co.jalin.seconsole.engine.dto.SocketSummary;
import id.co.jalin.seconsole.engine.history.EngineChannelHistoryService;
import id.co.jalin.seconsole.engine.history.EngineChannelSocketSampleEntity;
import id.co.jalin.seconsole.engine.model.ChannelCfg;
import id.co.jalin.seconsole.engine.model.ChannelSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only endpoints for the channels feature. Rewritten in Chat 3e-3 to
 * consume the new {@link EngineChannelSnapshotService} cache (sourced from
 * {@code /socket/snapshot/channels}) and the H2-backed history tables
 * instead of the pre-rewrite in-memory ring buffer.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/channels} — list view; grouped {@link ChannelSummary}</li>
 *   <li>{@code GET /api/channels/{name}} — detail; one {@link ChannelSummary}</li>
 *   <li>{@code GET /api/channels/{name}/history?window=4m} — time-series from H2</li>
 *   <li>{@code GET /api/channels/snapshot} — raw {@link ChannelSnapshot} pass-through</li>
 *   <li>{@code GET /api/engine/health} — engine health badge (unchanged)</li>
 * </ul>
 *
 * <p>All endpoints serve from the in-memory cache — engine is never called
 * synchronously. If the engine is unreachable, last-known data is returned
 * with {@code reachable=false}.
 */
@RestController
@RequestMapping("/api")
public class ChannelsController {

    private final EngineChannelSnapshotService snapshotService;
    private final EngineHealthService healthService;
    private final EngineChannelHistoryService historyService;
    private final EngineConfigService configService;

    public ChannelsController(EngineChannelSnapshotService snapshotService,
                              EngineHealthService healthService,
                              EngineChannelHistoryService historyService,
                              EngineConfigService configService) {
        this.snapshotService = snapshotService;
        this.healthService = healthService;
        this.historyService = historyService;
        this.configService = configService;
    }

    // =========================================================================
    // List & detail
    // =========================================================================

    @GetMapping("/channels")
    public ResponseEntity<ChannelsResponse> list() {
        Snapshot snap = snapshotService.currentSnapshot();
        return ResponseEntity.ok(new ChannelsResponse(
                snap.channels(), snap.reachable(),
                snap.lastUpdateMillis(), snap.lastError()));
    }

    @GetMapping("/channels/{name}")
    public ResponseEntity<?> detail(@PathVariable String name) {
        ChannelSummary c = snapshotService.findChannel(name);
        if (c == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "channel_not_found",
                    "name", name));
        }
        return ResponseEntity.ok(c);
    }

    /**
     * {@code GET /api/channels/{name}/config} — static channel configuration
     * (listen host/port, client strategy, pool endpoints). Populated on a
     * slow cadence by {@link EngineConfigService} from the engine's
     * {@code /config/channels} endpoint. Shape mirrors {@link ChannelCfg} —
     * frontend's {@code ChannelConfigResponse} has identical fields.
     */
    @GetMapping("/channels/{name}/config")
    public ResponseEntity<?> config(@PathVariable String name) {
        ChannelCfg cfg = configService.currentConfigByName().get(name);
        if (cfg == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "channel_config_not_found",
                    "name", name));
        }
        return ResponseEntity.ok(cfg);
    }

    /** Raw snapshot pass-through — mirrors the engine wire shape. */
    @GetMapping("/channels/snapshot")
    public ResponseEntity<?> snapshot() {
        Snapshot snap = snapshotService.currentSnapshot();
        if (snap.raw() == null) {
            // Poller hasn't produced a first sample yet.
            return ResponseEntity.status(503).body(Map.of(
                    "error", "engine_not_ready",
                    "reachable", snap.reachable(),
                    "lastError", snap.lastError() == null ? "" : snap.lastError()));
        }
        return ResponseEntity.ok(snap.raw());
    }

    @GetMapping("/engine/health")
    public ResponseEntity<EngineHealthResponse> health() {
        EngineHealthService.State st = healthService.current();
        return ResponseEntity.ok(new EngineHealthResponse(
                st.reachable(), st.status(), st.role(), st.mode(),
                healthService.baseUrl(), st.lastPollMs(), st.lastError()));
    }

    // =========================================================================
    // History — H2-backed time-series
    // =========================================================================

    /**
     * {@code GET /api/channels/{name}/history?window=4m} — time-series data
     * for the Metrics tab charts.
     *
     * <p>Window is parsed as a short form ({@code 30s}, {@code 4m},
     * {@code 1h}); unknown → 4m default. The backend queries the
     * {@code engine_channel_socket_sample} table for rows matching any
     * hashId under this channel and returns them grouped per socket.
     */
    @GetMapping("/channels/{name}/history")
    public ResponseEntity<?> history(
            @PathVariable String name,
            @RequestParam(name = "window", defaultValue = "4m") String window) {

        ChannelSummary c = snapshotService.findChannel(name);
        if (c == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "channel_not_found",
                    "name", name));
        }

        List<EndpointRef> endpoints = buildEndpointRefs(c);
        Set<String> hashIds = new LinkedHashSet<>();
        for (EndpointRef r : endpoints) hashIds.add(r.hashId());

        long windowMs = parseWindow(window);
        Instant to = Instant.now();
        Instant from = to.minusMillis(windowMs);

        List<EngineChannelSocketSampleEntity> rows =
                historyService.samplesBetween(hashIds, from, to);

        Map<String, List<HistorySample>> byHash = groupSamplesByHashId(rows, hashIds);

        return ResponseEntity.ok(new ChannelHistoryResponse(
                name, windowMs, to.toEpochMilli(),
                endpoints, byHash));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static List<EndpointRef> buildEndpointRefs(ChannelSummary c) {
        // LinkedHashMap preserves insertion order; dedupe on hashId.
        Map<String, EndpointRef> byHash = new LinkedHashMap<>();
        for (SocketSummary s : c.servers()) byHash.putIfAbsent(s.hashId(), toRef(s));
        for (SocketSummary s : c.clients()) byHash.putIfAbsent(s.hashId(), toRef(s));
        return new ArrayList<>(byHash.values());
    }

    private static EndpointRef toRef(SocketSummary s) {
        return new EndpointRef(
                s.hashId(),
                shortLabel(s),
                s.socketId(),
                s.type(),
                s.runtime() == null ? "UNKNOWN" : s.runtime().state());
    }

    /**
     * Produce a compact legend label from a socket id or remote host.
     * <ul>
     *   <li>{@code fello-server-27000} → {@code server-27000}</li>
     *   <li>{@code fello-client-13.131.9.166-26000} → {@code .166:26000}</li>
     * </ul>
     */
    private static String shortLabel(SocketSummary s) {
        String id = s.socketId() == null ? "" : s.socketId();
        if ("SERVER".equalsIgnoreCase(s.type())) {
            int dash = id.indexOf("-server-");
            if (dash >= 0) return "server-" + id.substring(dash + "-server-".length());
            return id;
        }
        String remote = s.runtime() == null || s.runtime().remoteHost() == null
                ? "" : s.runtime().remoteHost();
        if (!remote.isEmpty()) {
            int colon = remote.lastIndexOf(':');
            String hostPart = colon >= 0 ? remote.substring(0, colon) : remote;
            String port = colon >= 0 ? remote.substring(colon) : "";
            int dot = hostPart.lastIndexOf('.');
            if (dot >= 0) return "." + hostPart.substring(dot + 1) + port;
            return hostPart + port;
        }
        return id;
    }

    /**
     * Project repository rows into the {@link HistorySample} shape, grouped
     * by hashId. Includes empty lists for hashIds the client asked about
     * but that had zero rows in the window (graceful rendering on the FE).
     */
    private static Map<String, List<HistorySample>> groupSamplesByHashId(
            List<EngineChannelSocketSampleEntity> rows, Set<String> expected) {

        Map<String, List<HistorySample>> out = new LinkedHashMap<>(expected.size() * 2);
        for (String h : expected) out.put(h, new ArrayList<>());

        for (EngineChannelSocketSampleEntity r : rows) {
            List<HistorySample> bucket = out.computeIfAbsent(
                    r.getHashId(), k -> new ArrayList<>());
            bucket.add(new HistorySample(
                    r.getCapturedAt().toEpochMilli(),
                    r.getState(),
                    r.getQueueDepth(),
                    new SocketSummary.Latency(
                            r.getLatAvgNs(), r.getLatMinNs(), r.getLatMaxNs(),
                            r.getLatP90Ns(), r.getLatP95Ns()),
                    new SocketSummary.Tps(
                            r.getPressureAvg(), r.getPressureMin(), r.getPressureMax(),
                            r.getPressureP90(), r.getPressureP95()),
                    new SocketSummary.Tps(
                            r.getThroughputAvg(), r.getThroughputMin(), r.getThroughputMax(),
                            r.getThroughputP90(), r.getThroughputP95())
            ));
        }
        return out;
    }

    /**
     * Parse short-form windows like {@code 4m}, {@code 30s}, {@code 1h}.
     * Unknown/malformed → 4-minute default.
     */
    private static long parseWindow(String raw) {
        if (raw == null || raw.isBlank()) return 4 * 60_000L;
        String w = raw.trim().toLowerCase();
        try {
            char suffix = w.charAt(w.length() - 1);
            long n = Long.parseLong(w.substring(0, w.length() - 1));
            return switch (suffix) {
                case 's' -> n * 1_000L;
                case 'm' -> n * 60_000L;
                case 'h' -> n * 3_600_000L;
                default  -> 4 * 60_000L;
            };
        } catch (Exception ex) {
            return 4 * 60_000L;
        }
    }

    // =========================================================================
    // Response envelopes
    // =========================================================================

    public record ChannelsResponse(
            List<ChannelSummary> channels,
            boolean reachable,
            long lastUpdateMillis,
            String lastError
    ) {}

    public record EngineHealthResponse(
            boolean reachable,
            String status,
            String role,
            String mode,
            String baseUrl,
            long lastPollMs,
            String lastError
    ) {}

    /** Retained for compilation — not currently exposed. */
    @SuppressWarnings("unused")
    private static Set<String> toHashSet(List<EndpointRef> refs) {
        Set<String> out = new HashSet<>(refs.size() * 2);
        for (EndpointRef r : refs) out.add(r.hashId());
        return out;
    }
}
