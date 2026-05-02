package id.co.jalin.seconsole.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * JVM-internal metrics snapshot. Returned by GET /api/system/jvm.
 * Sourced from JMX/MBean API via se-core JvmMetricsCollector.
 *
 * Fields may be null when a value cannot be computed (e.g. usedPercent when max = -1).
 * Frontend displays "N/A" for nulls.
 */
public record JvmMetricsDto(
        Instant          timestamp,
        String           gcImpl,
        JvmProcessInfo   process,
        HeapMetrics      heap,
        HeapMetrics      nonHeap,
        List<MemoryPoolMetrics>  pools,
        List<GcCollectorMetrics> gc,
        GcSummary        gcSummary,
        ThreadMetrics    threads,
        ClassMetrics     classes,
        List<BufferPoolMetrics>  buffers
) {

    /** JVM process identity. Stable across snapshots. */
    public record JvmProcessInfo(
            long   pid,
            long   startTime,    // epoch millis
            long   uptimeMs,
            String javaVersion,
            String vmName,
            String vmVendor
    ) {}

    /**
     * Top-level memory area (heap or non-heap) as reported by MemoryMXBean.
     * usedPercent is null when maxBytes = -1 (non-heap: no upper bound defined).
     */
    public record HeapMetrics(
            long   initBytes,
            long   usedBytes,
            long   committedBytes,
            long   maxBytes,       // -1 if unlimited
            Double usedPercent     // null if maxBytes = -1
    ) {}

    /**
     * Per-region memory pool as reported by MemoryPoolMXBean.
     * G1GC note: Eden/Survivor usedBytes may exceed committedBytes — use heap.usedBytes for totals.
     */
    public record MemoryPoolMetrics(
            String name,
            String poolType,       // "HEAP" | "NON_HEAP"
            long   usedBytes,
            long   committedBytes,
            long   maxBytes        // -1 if dynamic or unlimited
    ) {}

    /**
     * Per-GC-collector stats. Values are cumulative since JVM start.
     * Consumers compute per-interval deltas by diffing successive snapshots.
     *
     * concurrent = true  → runs alongside app threads; collectionTimeMs is not a pause duration.
     * concurrent = false → stop-the-world; collectionTimeMs = total pause time accumulated.
     */
    public record GcCollectorMetrics(
            String  name,
            boolean concurrent,
            long    collectionCount,
            long    collectionTimeMs
    ) {}

    /**
     * Derived GC health summary — computed from STW collectors only.
     * gcOverheadPercent = total STW pause time / JVM uptime * 100.
     * fullGcOccurred = true if any Old/Major stop-the-world collector has run since JVM start.
     */
    public record GcSummary(
            long   totalStwTimeMs,
            Double gcOverheadPercent,
            boolean fullGcOccurred
    ) {}

    /** Thread counts and state breakdown. nonDaemon = current - daemon. */
    public record ThreadMetrics(
            int current,
            int daemon,
            int nonDaemon,
            int peak,
            int totalStarted,
            int deadlocked,   // 0 = healthy; >0 = critical
            int runnable,
            int blocked,
            int waiting,
            int timedWaiting
    ) {}

    /** Class loading counters. All values cumulative since JVM start. */
    public record ClassMetrics(
            int loaded,
            int totalLoaded,
            int unloaded
    ) {}

    /**
     * NIO buffer pool ("direct" | "mapped").
     * Monotonically growing direct.count is an off-heap leak indicator.
     */
    public record BufferPoolMetrics(
            String name,
            int    count,
            long   usedBytes,
            long   totalCapacityBytes
    ) {}
}
