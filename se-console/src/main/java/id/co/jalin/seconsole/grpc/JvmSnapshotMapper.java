package id.co.jalin.seconsole.grpc;

import com.socket.edge.grpc.*;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.HeapMetrics;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.MemoryPoolMetrics;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.GcCollectorMetrics;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.GcSummary;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.ThreadMetrics;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.ClassMetrics;
import id.co.jalin.seconsole.dto.response.JvmMetricsDto.BufferPoolMetrics;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Maps JvmSnapshot (from se-core gRPC stream) to JvmMetricsDto.
 * Mirrors OsSnapshotMapper structure: one method per metric category.
 *
 * Note: JvmMetricsDto.JvmProcessInfo is used with full qualifier to avoid
 * ambiguity with com.socket.edge.grpc.JvmProcessInfo (same name, different type).
 */
@Component
public class JvmSnapshotMapper {

    public JvmMetricsDto toDto(JvmSnapshot jvm) {
        return new JvmMetricsDto(
                Instant.ofEpochMilli(jvm.getHeader().getCapturedAt()),
                toGcImplString(jvm.getGcImpl()),
                mapProcess(jvm.getProcess()),
                mapMemoryArea(jvm.getHeap()),
                mapMemoryArea(jvm.getNonHeap()),
                mapPools(jvm.getPoolsList()),
                mapGc(jvm.getGcList()),
                mapGcSummary(jvm.getGcList(), jvm.getProcess().getUptimeMs()),
                mapThreads(jvm.getThreads()),
                mapClasses(jvm.getClasses()),
                mapBuffers(jvm.getBuffersList())
        );
    }

    // ── Process ──────────────────────────────────────────────────────────────

    private JvmMetricsDto.JvmProcessInfo mapProcess(JvmProcessInfo p) {
        return new JvmMetricsDto.JvmProcessInfo(
                p.getPid(),
                p.getStartTime(),
                p.getUptimeMs(),
                p.getJavaVersion(),
                p.getVmName(),
                p.getVmVendor()
        );
    }

    // ── Memory areas ─────────────────────────────────────────────────────────

    private HeapMetrics mapMemoryArea(JvmMemoryArea area) {
        long max = area.getMaxBytes();
        Double pct = max > 0 ? round((double) area.getUsedBytes() / max * 100.0) : null;
        return new HeapMetrics(
                area.getInitBytes(),
                area.getUsedBytes(),
                area.getCommittedBytes(),
                max,
                pct
        );
    }

    // ── Memory pools ─────────────────────────────────────────────────────────

    private List<MemoryPoolMetrics> mapPools(List<JvmMemoryPool> pools) {
        return pools.stream()
                .map(p -> new MemoryPoolMetrics(
                        p.getName(),
                        p.getPoolType() == MemoryPoolType.MEMORY_POOL_TYPE_HEAP ? "HEAP" : "NON_HEAP",
                        p.getUsedBytes(),
                        p.getCommittedBytes(),
                        p.getMaxBytes()
                ))
                .toList();
    }

    // ── GC collectors ────────────────────────────────────────────────────────

    private List<GcCollectorMetrics> mapGc(List<JvmGcCollector> gcList) {
        return gcList.stream()
                .map(gc -> new GcCollectorMetrics(
                        gc.getName(),
                        gc.getIsConcurrent(),
                        gc.getCollectionCount(),
                        gc.getCollectionTimeMs()
                ))
                .toList();
    }

    private GcSummary mapGcSummary(List<JvmGcCollector> gcList, long uptimeMs) {
        long totalStwMs = gcList.stream()
                .filter(gc -> !gc.getIsConcurrent())
                .mapToLong(JvmGcCollector::getCollectionTimeMs)
                .sum();

        Double overhead = uptimeMs > 0 ? round((double) totalStwMs / uptimeMs * 100.0) : null;

        boolean fullGcOccurred = gcList.stream()
                .anyMatch(gc -> !gc.getIsConcurrent()
                        && (gc.getName().contains("Old") || gc.getName().contains("Major"))
                        && gc.getCollectionCount() > 0);

        return new GcSummary(totalStwMs, overhead, fullGcOccurred);
    }

    // ── Threads ──────────────────────────────────────────────────────────────

    private ThreadMetrics mapThreads(JvmThreadStats t) {
        return new ThreadMetrics(
                t.getCurrent(),
                t.getDaemon(),
                t.getCurrent() - t.getDaemon(),
                t.getPeak(),
                t.getTotalStarted(),
                t.getDeadlocked(),
                t.getRunnable(),
                t.getBlocked(),
                t.getWaiting(),
                t.getTimedWaiting()
        );
    }

    // ── Classes ──────────────────────────────────────────────────────────────

    private ClassMetrics mapClasses(JvmClassStats c) {
        return new ClassMetrics(c.getLoaded(), c.getTotalLoaded(), c.getUnloaded());
    }

    // ── Buffer pools ─────────────────────────────────────────────────────────

    private List<BufferPoolMetrics> mapBuffers(List<JvmBufferPool> buffers) {
        return buffers.stream()
                .map(b -> new BufferPoolMetrics(
                        b.getName(),
                        b.getCount(),
                        b.getUsedBytes(),
                        b.getTotalCapacityBytes()
                ))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String toGcImplString(GcImplementation impl) {
        return switch (impl) {
            case GC_IMPL_G1GC       -> "G1GC";
            case GC_IMPL_ZGC        -> "ZGC";
            case GC_IMPL_SHENANDOAH -> "Shenandoah";
            case GC_IMPL_PARALLEL   -> "Parallel";
            case GC_IMPL_SERIAL     -> "Serial";
            default                 -> "Unknown";
        };
    }

    private static Double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return null;
        return Math.round(v * 10.0) / 10.0;
    }
}
