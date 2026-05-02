package id.co.jalin.seconsole.engine.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.co.jalin.seconsole.engine.model.JvmSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Persists engine JVM snapshots to the {@code engine_jvm_snapshot} H2 table.
 *
 * <p>Two record() overloads:
 * <ul>
 *   <li>{@link #record(JvmSnapshot)} — legacy HTTP model path (kept for compatibility).</li>
 *   <li>{@link #record(com.socket.edge.grpc.JvmSnapshot)} — gRPC proto path,
 *       called by EngineJvmSnapshotService after switching to the gRPC stream.</li>
 * </ul>
 *
 * <p>Retention is enforced via a once-per-hour {@link #prune()} scheduler
 * that deletes rows older than the configured cut-off. Default is 7 days;
 * tune with {@code seconsole.engine.jvm.history.retention-days}.
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineJvmHistoryService {

    private static final Logger log = LoggerFactory.getLogger(EngineJvmHistoryService.class);

    private final EngineJvmSnapshotRepository repository;
    private final ObjectMapper mapper;
    private final int retentionDays;

    public EngineJvmHistoryService(
            EngineJvmSnapshotRepository repository,
            ObjectMapper mapper,
            @Value("${seconsole.engine.jvm.history.retention-days:7}") int retentionDays) {
        this.repository = repository;
        this.mapper = mapper;
        this.retentionDays = Math.max(1, retentionDays);
    }

    // ── record() — HTTP model (legacy, kept for compatibility) ───────────────

    @Transactional
    public void record(JvmSnapshot snap) {
        if (snap == null) return;
        try {
            repository.save(toEntityFromHttp(snap));
        } catch (Exception ex) {
            log.warn("Failed to persist engine JVM snapshot: {}", ex.getMessage());
        }
    }

    // ── record() — gRPC proto path (active path) ──────────────────────────────

    @Transactional
    public void record(com.socket.edge.grpc.JvmSnapshot grpcSnap) {
        if (grpcSnap == null) return;
        try {
            repository.save(toEntityFromGrpc(grpcSnap));
        } catch (Exception ex) {
            log.warn("Failed to persist engine JVM gRPC snapshot: {}", ex.getMessage());
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Cleanup job — one pass per hour, at :17 past the hour. */
    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void prune() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try {
            int deleted = repository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Pruned {} engine JVM snapshot rows older than {}", deleted, cutoff);
            }
        } catch (Exception ex) {
            log.warn("Prune failed: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<EngineJvmSnapshotEntity> recent(int limit) {
        List<EngineJvmSnapshotEntity> all = repository.findTop500ByOrderByCapturedAtDesc();
        if (limit >= all.size()) return all;
        return all.subList(0, Math.max(0, limit));
    }

    @Transactional(readOnly = true)
    public List<EngineJvmSnapshotEntity> between(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)) return Collections.emptyList();
        return repository.findByCapturedAtBetweenOrderByCapturedAtAsc(from, to);
    }

    // ── Entity mappers ────────────────────────────────────────────────────────

    private EngineJvmSnapshotEntity toEntityFromHttp(JvmSnapshot s) {
        EngineJvmSnapshotEntity e = new EngineJvmSnapshotEntity();

        e.setCapturedAt(Instant.ofEpochMilli(
                s.capturedAt() > 0 ? s.capturedAt() : System.currentTimeMillis()));
        e.setSnapshotId(s.snapshotId());

        JvmSnapshot.EngineProcess p = s.engineProcess();
        if (p != null) {
            e.setPid(p.pid());
            e.setUptimeMs(p.uptime());
            e.setJavaVersion(trim(p.javaVersion(), 64));
            e.setVmName(trim(p.vmName(), 128));
        }

        JvmSnapshot.MemoryArea h = s.heap();
        if (h != null) {
            e.setHeapUsed(h.used());
            e.setHeapCommitted(h.committed());
            e.setHeapMax(h.max());
            e.setHeapInit(h.init());
        }
        JvmSnapshot.MemoryArea nh = s.nonHeap();
        if (nh != null) {
            e.setNonheapUsed(nh.used());
            e.setNonheapCommitted(nh.committed());
            e.setNonheapMax(nh.max());
            e.setNonheapInit(nh.init());
        } else {
            e.setNonheapMax(-1);
        }

        JvmSnapshot.Threads t = s.threads();
        if (t != null) {
            e.setThreadsCurrent(t.current());
            e.setThreadsDaemon(t.daemon());
            e.setThreadsPeak(t.peak());
            e.setThreadsTotalStarted(t.totalStarted());
            e.setThreadsDeadlocked(t.deadlocked());
            JvmSnapshot.Threads.ByState bs = t.byState();
            if (bs != null) {
                e.setThreadsRunnable(bs.RUNNABLE());
                e.setThreadsBlocked(bs.BLOCKED());
                e.setThreadsWaiting(bs.WAITING());
                e.setThreadsTimedWaiting(bs.TIMED_WAITING());
            }
        }

        JvmSnapshot.Classes c = s.classes();
        if (c != null) {
            e.setClassesLoaded(c.loaded());
            e.setClassesTotalLoaded(c.totalLoaded());
            e.setClassesUnloaded(c.unloaded());
        }

        long gcTotalCount = 0, gcTotalTime = 0;
        if (s.gc() != null) {
            for (JvmSnapshot.Gc g : s.gc()) {
                if (g == null) continue;
                gcTotalCount += g.collectionCount();
                gcTotalTime  += g.collectionTimeMs();
            }
        }
        e.setGcTotalCount(gcTotalCount);
        e.setGcTotalTimeMs(gcTotalTime);

        e.setPoolsJson(writeJson(s.memoryPools()));
        e.setGcJson(writeJson(s.gc()));
        e.setBuffersJson(writeJson(s.buffers()));

        return e;
    }

    private EngineJvmSnapshotEntity toEntityFromGrpc(com.socket.edge.grpc.JvmSnapshot jvm) {
        EngineJvmSnapshotEntity e = new EngineJvmSnapshotEntity();

        e.setCapturedAt(Instant.ofEpochMilli(jvm.getHeader().getCapturedAt()));
        e.setSnapshotId(jvm.getHeader().getSnapshotId());

        com.socket.edge.grpc.JvmProcessInfo p = jvm.getProcess();
        e.setPid((long) p.getPid());
        e.setUptimeMs(p.getUptimeMs());
        e.setJavaVersion(trim(p.getJavaVersion(), 64));
        e.setVmName(trim(p.getVmName(), 128));

        com.socket.edge.grpc.JvmMemoryArea heap = jvm.getHeap();
        e.setHeapUsed(heap.getUsedBytes());
        e.setHeapCommitted(heap.getCommittedBytes());
        e.setHeapMax(heap.getMaxBytes());
        e.setHeapInit(heap.getInitBytes());

        com.socket.edge.grpc.JvmMemoryArea nonHeap = jvm.getNonHeap();
        e.setNonheapUsed(nonHeap.getUsedBytes());
        e.setNonheapCommitted(nonHeap.getCommittedBytes());
        e.setNonheapMax(nonHeap.getMaxBytes() > 0 ? nonHeap.getMaxBytes() : -1L);
        e.setNonheapInit(nonHeap.getInitBytes());

        com.socket.edge.grpc.JvmThreadStats t = jvm.getThreads();
        e.setThreadsCurrent(t.getCurrent());
        e.setThreadsDaemon(t.getDaemon());
        e.setThreadsPeak(t.getPeak());
        e.setThreadsTotalStarted(t.getTotalStarted());
        e.setThreadsDeadlocked(t.getDeadlocked());
        e.setThreadsRunnable(t.getRunnable());
        e.setThreadsBlocked(t.getBlocked());
        e.setThreadsWaiting(t.getWaiting());
        e.setThreadsTimedWaiting(t.getTimedWaiting());

        com.socket.edge.grpc.JvmClassStats c = jvm.getClasses();
        e.setClassesLoaded(c.getLoaded());
        e.setClassesTotalLoaded(c.getTotalLoaded());
        e.setClassesUnloaded(c.getUnloaded());

        long gcCount = 0, gcTime = 0;
        for (com.socket.edge.grpc.JvmGcCollector gc : jvm.getGcList()) {
            gcCount += gc.getCollectionCount();
            gcTime  += gc.getCollectionTimeMs();
        }
        e.setGcTotalCount(gcCount);
        e.setGcTotalTimeMs(gcTime);

        e.setPoolsJson(writeJson(jvm.getPoolsList()));
        e.setGcJson(writeJson(jvm.getGcList()));
        e.setBuffersJson(writeJson(jvm.getBuffersList()));

        return e;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.debug("JSON serialization failed: {}", ex.getMessage());
            return null;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
