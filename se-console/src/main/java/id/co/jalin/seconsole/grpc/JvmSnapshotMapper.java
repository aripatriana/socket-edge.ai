package id.co.jalin.seconsole.grpc;

import com.socket.edge.grpc.*;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Maps JvmSnapshot (from se-core gRPC stream) to JvmMetricsDto.
 *
 * Fields not present in gRPC proto are substituted with safe defaults:
 *  - GcCollector.poolNames → empty list (proto only carries aggregate counts)
 *  - RuntimeInfo.specVersion → empty string
 *  - RuntimeInfo.inputArguments → empty list (not forwarded by engine)
 *  - DeadlockInfo.threads → empty list (proto has count only, no thread detail)
 */
@Component
public class JvmSnapshotMapper {

    public JvmMetricsDto toDto(JvmSnapshot jvm) {
        JvmThreadStats t = jvm.getThreads();
        return new JvmMetricsDto(
                Instant.ofEpochMilli(jvm.getHeader().getCapturedAt()),
                mapMemoryArea(jvm.getHeap()),
                mapMemoryArea(jvm.getNonHeap()),
                mapPools(jvm.getPoolsList()),
                mapGc(jvm.getGcList()),
                mapThreads(t),
                mapClasses(jvm.getClasses()),
                mapRuntime(jvm.getProcess()),
                new DeadlockInfo(t.getDeadlocked(), Collections.emptyList())
        );
    }

    // ── Memory areas ─────────────────────────────────────────────────────────

    private MemoryArea mapMemoryArea(JvmMemoryArea area) {
        long max = area.getMaxBytes();
        Double pct = max > 0 ? round((double) area.getUsedBytes() / max * 100.0) : null;
        return new MemoryArea(
                area.getUsedBytes(),
                area.getCommittedBytes(),
                max,
                area.getInitBytes(),
                pct
        );
    }

    // ── Memory pools ─────────────────────────────────────────────────────────

    private List<MemoryPool> mapPools(List<JvmMemoryPool> pools) {
        return pools.stream()
                .map(p -> {
                    String type = p.getPoolType() == MemoryPoolType.MEMORY_POOL_TYPE_HEAP
                            ? "HEAP" : "NON_HEAP";
                    long max = p.getMaxBytes();
                    Double pct = max > 0 ? round((double) p.getUsedBytes() / max * 100.0) : null;
                    return new MemoryPool(
                            p.getName(), type,
                            p.getUsedBytes(), p.getCommittedBytes(), max, pct);
                })
                .toList();
    }

    // ── GC collectors ────────────────────────────────────────────────────────

    private List<GcCollector> mapGc(List<JvmGcCollector> gcList) {
        return gcList.stream()
                .map(gc -> new GcCollector(
                        gc.getName(),
                        gc.getCollectionCount(),
                        gc.getCollectionTimeMs(),
                        Collections.emptyList()
                ))
                .toList();
    }

    // ── Threads ──────────────────────────────────────────────────────────────

    private ThreadMetrics mapThreads(JvmThreadStats t) {
        return new ThreadMetrics(
                t.getCurrent(),
                t.getDaemon(),
                t.getPeak(),
                t.getTotalStarted(),
                t.getBlocked(),
                t.getWaiting(),
                t.getTimedWaiting(),
                t.getRunnable()
        );
    }

    // ── Classes ──────────────────────────────────────────────────────────────

    private ClassMetrics mapClasses(JvmClassStats c) {
        return new ClassMetrics(c.getLoaded(), c.getTotalLoaded(), c.getUnloaded());
    }

    // ── Runtime (built from process info) ────────────────────────────────────

    private RuntimeInfo mapRuntime(JvmProcessInfo p) {
        return new RuntimeInfo(
                p.getVmName(),
                p.getVmVendor(),
                p.getJavaVersion(),
                "",
                p.getUptimeMs(),
                p.getStartTime(),
                Collections.emptyList()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return null;
        return Math.round(v * 10.0) / 10.0;
    }
}
