package id.co.jalin.seconsole.engine.history;

import id.co.jalin.seconsole.engine.dto.SocketSummary;
import id.co.jalin.seconsole.engine.model.ChannelSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Persists channel snapshots to the {@code engine_channel_snapshot} +
 * {@code engine_channel_socket_sample} parent-child tables.
 *
 * <p>Architecture mirrors {@link EngineJvmHistoryService}: the snapshot
 * service calls {@link #record} synchronously inside its poll tick, right
 * after updating the AtomicReference cache. DB failures are logged but
 * swallowed — the dashboard path must not break on a DB hiccup.
 *
 * <p><b>Retention &amp; prune.</b> Header rows drive the cascade — we only
 * need to delete headers; the FK deletes child rows automatically. To avoid
 * locking the table for a multi-million-row prune in one statement, we
 * delete in chunks of {@value #PRUNE_BATCH_SIZE}. Pruner runs hourly.
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineChannelHistoryService {

    private static final Logger log = LoggerFactory.getLogger(EngineChannelHistoryService.class);

    private static final int PRUNE_BATCH_SIZE = 1_000;   // header rows per delete call
    private static final int PRUNE_MAX_BATCHES = 200;    // safety cap → at most 200k headers per prune pass

    private final EngineChannelSnapshotRepository headerRepo;
    private final EngineChannelSocketSampleRepository sampleRepo;
    private final int retentionDays;

    public EngineChannelHistoryService(
        EngineChannelSnapshotRepository headerRepo,
        EngineChannelSocketSampleRepository sampleRepo,
        @Value("${seconsole.engine.channels.history.retention-days:7}") int retentionDays) {
        this.headerRepo = headerRepo;
        this.sampleRepo = sampleRepo;
        this.retentionDays = Math.max(1, retentionDays);
    }

    /**
     * Persist one poll tick: one header row and N socket sample rows.
     * Both writes are inside a single transaction so the header and its
     * children always appear atomically.
     *
     * <p>The mapped {@code channels} parameter is not used here — we derive
     * everything needed from the raw snapshot. We accept the grouped list
     * just to keep channel-name lookup efficient should the persistence
     * shape ever evolve to include it.
     */
    @Transactional
    public void record(ChannelSnapshot snap) {
        if (snap == null) {
            log.debug("record() called with null snapshot — skipping");
            return;
        }
        try {
            EngineChannelSnapshotEntity header = toHeader(snap);
            // saveAndFlush is critical here: downstream sample rows carry
            // header.getId() as their FK, and a plain save() defers the INSERT
            // until transaction commit — leaving getId() null and the
            // children violating the NOT NULL / FK constraint.
            headerRepo.saveAndFlush(header);
            Long headerId = header.getId();
            Instant capturedAt = header.getCapturedAt();

            int sampleCount = 0;
            if (snap.sockets() != null && !snap.sockets().isEmpty()) {
                List<EngineChannelSocketSampleEntity> samples = new ArrayList<>(snap.sockets().size());
                for (ChannelSnapshot.Socket s : snap.sockets()) {
                    if (s == null || s.bindingId() == null) continue;
                    samples.add(toSample(headerId, capturedAt, s));
                }
                sampleRepo.saveAll(samples);
                sampleCount = samples.size();
            }
            // INFO level so it shows up without log-level tweaks; downgrade
            // to DEBUG once the ingest path is confirmed working.
            log.info("Persisted channel snapshot header={} samples={} capturedAt={}",
                headerId, sampleCount, capturedAt);
        } catch (Exception ex) {
            // Include the full exception so Hibernate constraint violations
            // and transport errors don't vanish behind a terse message-only log.
            log.warn("Failed to persist channel snapshot", ex);
        }
    }

    // =========================================================================
    // Time-series queries for the Metrics tab charts
    // =========================================================================

    @Transactional(readOnly = true)
    public List<EngineChannelSocketSampleEntity> samplesBetween(
        Collection<String> bindingIds, Instant from, Instant to) {
        if (bindingIds == null || bindingIds.isEmpty() || from == null || to == null || from.isAfter(to)) {
            return Collections.emptyList();
        }
        return sampleRepo.findForSocketsBetween(bindingIds, from, to);
    }

    @Transactional(readOnly = true)
    public List<EngineChannelSnapshotEntity> recentHeaders() {
        return headerRepo.findTop200ByOrderByCapturedAtDesc();
    }

    // =========================================================================
    // Retention
    // =========================================================================

    /** Runs on the hour, at :23 past, staggered vs other pruners. */
    @Scheduled(cron = "0 23 * * * *")
    public void prune() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int totalDeleted = 0;
        for (int batch = 0; batch < PRUNE_MAX_BATCHES; batch++) {
            int deleted = deleteBatch(cutoff);
            if (deleted <= 0) break;
            totalDeleted += deleted;
        }
        if (totalDeleted > 0) {
            log.info("Pruned {} channel-snapshot header rows older than {} (+ cascading children)",
                totalDeleted, cutoff);
        }
    }

    /**
     * One prune batch. Separated into its own transactional method so each
     * chunk commits independently — a crash mid-prune leaves partial
     * progress committed rather than rolling back everything.
     */
    @Transactional
    int deleteBatch(Instant cutoff) {
        List<Long> ids = headerRepo.findIdsOlderThan(
            cutoff, PageRequest.of(0, PRUNE_BATCH_SIZE));
        if (ids.isEmpty()) return 0;
        return headerRepo.deleteByIds(ids);
    }

    // =========================================================================
    // Mapping helpers
    // =========================================================================

    private static EngineChannelSnapshotEntity toHeader(ChannelSnapshot snap) {
        EngineChannelSnapshotEntity h = new EngineChannelSnapshotEntity();
        h.setCapturedAt(Instant.ofEpochMilli(
            snap.capturedAt() > 0 ? snap.capturedAt() : System.currentTimeMillis()));
        h.setSnapshotId(trim(snap.snapshotId(), 64));
        h.setCaptureDurationMs(snap.captureDurationMs());

        ChannelSnapshot.Aggregate a = snap.aggregate();
        if (a != null) {
            h.setSocketCount(a.socketCount());
            h.setSocketsUp(a.socketsUp());
            h.setSocketsDown(a.socketsDown());
            h.setTotalActiveChannels(a.totalActiveChannels());
            h.setTotalMsgIn(a.totalMsgIn());
            h.setTotalMsgOut(a.totalMsgOut());
            h.setTotalQueueDepth(a.totalQueueDepth());
            h.setTotalErrCount(a.totalErrCount());
            h.setAvgPressureTps(a.avgPressureTps());
            h.setAvgThroughputTps(a.avgThroughputTps());
        } else if (snap.sockets() != null) {
            // Engine didn't include an aggregate block — derive the two socket counts
            // so the dashboard's header strip still has something sensible.
            h.setSocketCount(snap.sockets().size());
        }
        return h;
    }

    private static EngineChannelSocketSampleEntity toSample(
        Long headerId, Instant capturedAt, ChannelSnapshot.Socket s) {

        EngineChannelSocketSampleEntity e = new EngineChannelSocketSampleEntity();
        e.setSnapshotHeaderId(headerId);
        e.setCapturedAt(capturedAt);
        // binding_id, socket_id, channel_name, socket_type are all NOT NULL in
        // V3. Coerce missing values to a short placeholder rather than letting
        // Hibernate throw on insert — the ingest path must never break on a
        // malformed engine payload.
        e.setBindingId(nonNullTrim(s.bindingId(), 16, "?"));
        e.setSocketId(nonNullTrim(s.socketId(), 128, "unknown"));
        e.setChannelName(nonNullTrim(s.name(), 64, "unknown"));
        e.setSocketType(nonNullTrim(s.type(), 8, "CLIENT"));

        ChannelSnapshot.Runtime r = s.runtime();
        if (r != null) {
            e.setState(trim(r.state() == null ? "DOWN" : r.state(), 16));
            e.setLocalHost(trim(r.localHost(), 64));
            e.setRemoteHost(trim(r.remoteHost(), 256));
            e.setActiveChannels(r.activeChannels());
            e.setStartTime(r.startTime());
            e.setLastConnect(r.lastConnect());
            e.setLastDisconnect(r.lastDisconnect());
        } else {
            e.setState("DOWN");
        }

        ChannelSnapshot.Queue q = s.queue();
        if (q != null) {
            e.setMsgIn(q.msgIn());
            e.setMsgOut(q.msgOut());
            e.setQueueDepth(q.depth());
            e.setErrCount(q.errCount());
            e.setLastErr(q.lastErr());
            e.setLastMsg(q.lastMsg());
        }

        ChannelSnapshot.Metrics m = s.metrics();
        if (m != null) {
            ChannelSnapshot.Latency l = m.latency();
            if (l != null) {
                e.setLatAvgNs(l.avgNs());
                e.setLatMinNs(l.minNs());
                e.setLatMaxNs(l.maxNs());
                e.setLatP90Ns(l.p90Ns());
                e.setLatP95Ns(l.p95Ns());
            }
            ChannelSnapshot.Tps p = m.pressureTps();
            if (p != null) {
                e.setPressureAvg(p.avg());
                e.setPressureMin(p.min());
                e.setPressureMax(p.max());
                e.setPressureP90(p.p90());
                e.setPressureP95(p.p95());
            }
            ChannelSnapshot.Tps t = m.throughputTps();
            if (t != null) {
                e.setThroughputAvg(t.avg());
                e.setThroughputMin(t.min());
                e.setThroughputMax(t.max());
                e.setThroughputP90(t.p90());
                e.setThroughputP95(t.p95());
            }
        }
        return e;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Same as {@link #trim} but substitutes a fallback when input is null/blank. */
    private static String nonNullTrim(String s, int max, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Convenience for the controller — convert a batch of sample rows
     *  back into the DTO shape the frontend consumes. */
    public static List<SocketSummary.Metrics> toMetricsList(
        List<EngineChannelSocketSampleEntity> rows) {
        List<SocketSummary.Metrics> out = new ArrayList<>(rows.size());
        for (EngineChannelSocketSampleEntity r : rows) {
            out.add(new SocketSummary.Metrics(
                new SocketSummary.Stat(
                    r.getLatAvgNs(), r.getLatMinNs(), r.getLatMaxNs(),
                    r.getLatP90Ns(), r.getLatP95Ns(), 0L),
                new SocketSummary.Stat(
                    r.getPressureAvg(), r.getPressureMin(), r.getPressureMax(),
                    r.getPressureP90(), r.getPressureP95(), 0L),
                new SocketSummary.Stat(
                    r.getThroughputAvg(), r.getThroughputMin(), r.getThroughputMax(),
                    r.getThroughputP90(), r.getThroughputP95(), 0L)
            ));
        }
        return out;
    }

    /** For diagnostics — not used at runtime. */
    @SuppressWarnings("unused")
    private static Map<String, Object> unused() { return Collections.emptyMap(); }
}
