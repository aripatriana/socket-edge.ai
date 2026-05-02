package com.socket.edge.grpc.jvm;

import com.socket.edge.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Collects dynamic JVM metrics via JMX / MBean API on each call.
 *
 * Stateful: GcImplementation is resolved once at construction time.
 * GC counts are cumulative — consumers compute per-interval deltas.
 * Must be called from a single thread or with external sync.
 */
public class JvmMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(JvmMetricsCollector.class);

    private final RuntimeMXBean                runtimeMX;
    private final MemoryMXBean                 memoryMX;
    private final ThreadMXBean                 threadMX;
    private final ClassLoadingMXBean           classMX;
    private final List<MemoryPoolMXBean>       poolMXBeans;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    private final List<BufferPoolMXBean>       bufferMXBeans;
    private final GcImplementation             gcImpl;

    public JvmMetricsCollector() {
        this.runtimeMX     = ManagementFactory.getRuntimeMXBean();
        this.memoryMX      = ManagementFactory.getMemoryMXBean();
        this.threadMX      = ManagementFactory.getThreadMXBean();
        this.classMX       = ManagementFactory.getClassLoadingMXBean();
        this.poolMXBeans   = ManagementFactory.getMemoryPoolMXBeans();
        this.gcMXBeans     = ManagementFactory.getGarbageCollectorMXBeans();
        this.bufferMXBeans = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        this.gcImpl        = resolveGcImpl();
        log.info("JvmMetricsCollector initialized: gc_impl={} pools={}", gcImpl, poolMXBeans.size());
    }

    /** Collect a full JvmSnapshot. Safe to call repeatedly at any interval. */
    public JvmSnapshot collect(String snapshotId) {
        long now     = System.currentTimeMillis();
        long startNs = System.nanoTime();

        return JvmSnapshot.newBuilder()
                .setProcess(collectProcess())
                .setGcImpl(gcImpl)
                .setHeap(collectMemoryArea(memoryMX.getHeapMemoryUsage()))
                .setNonHeap(collectMemoryArea(memoryMX.getNonHeapMemoryUsage()))
                .addAllPools(collectPools())
                .addAllGc(collectGc())
                .setThreads(collectThreads())
                .setClasses(collectClasses())
                .addAllBuffers(collectBuffers())
                // Header built last so capture_duration covers full collection cost
                .setHeader(SnapshotHeader.newBuilder()
                        .setSnapshotId(snapshotId)
                        .setCapturedAt(now)
                        .setCaptureDuration((int) ((System.nanoTime() - startNs) / 1_000_000))
                        .build())
                .build();
    }

    // ── Process ──────────────────────────────────────────────────────────────

    private JvmProcessInfo collectProcess() {
        return JvmProcessInfo.newBuilder()
                .setPid(ProcessHandle.current().pid())
                .setStartTime(runtimeMX.getStartTime())
                .setUptimeMs(runtimeMX.getUptime())
                .setJavaVersion(runtimeMX.getVmVersion())
                .setVmName(runtimeMX.getVmName())
                .setVmVendor(runtimeMX.getVmVendor())
                .build();
    }

    // ── Memory area ──────────────────────────────────────────────────────────

    private JvmMemoryArea collectMemoryArea(MemoryUsage usage) {
        return JvmMemoryArea.newBuilder()
                .setInitBytes(usage.getInit())
                .setUsedBytes(usage.getUsed())
                .setCommittedBytes(usage.getCommitted())
                .setMaxBytes(usage.getMax())  // -1 if unlimited (non-heap)
                .build();
    }

    // ── Memory pools ─────────────────────────────────────────────────────────

    private List<JvmMemoryPool> collectPools() {
        List<JvmMemoryPool> result = new ArrayList<>();
        for (MemoryPoolMXBean pool : poolMXBeans) {
            try {
                MemoryUsage usage = pool.getUsage();
                if (usage == null) continue;
                result.add(JvmMemoryPool.newBuilder()
                        .setName(pool.getName())
                        .setPoolType(toProtoPoolType(pool.getType()))
                        .setUsedBytes(usage.getUsed())
                        .setCommittedBytes(usage.getCommitted())
                        .setMaxBytes(usage.getMax())
                        .build());
            } catch (Exception e) {
                log.debug("MemoryPool collection partial [{}]: {}", pool.getName(), e.getMessage());
            }
        }
        return result;
    }

    // ── GC collectors ────────────────────────────────────────────────────────

    private List<JvmGcCollector> collectGc() {
        List<JvmGcCollector> result = new ArrayList<>();
        for (GarbageCollectorMXBean gc : gcMXBeans) {
            result.add(JvmGcCollector.newBuilder()
                    .setName(gc.getName())
                    .setIsConcurrent(isConcurrent(gc.getName()))
                    .setCollectionCount(Math.max(0, gc.getCollectionCount()))
                    .setCollectionTimeMs(Math.max(0, gc.getCollectionTime()))
                    .build());
        }
        return result;
    }

    // ── Threads ──────────────────────────────────────────────────────────────

    private JvmThreadStats collectThreads() {
        JvmThreadStats.Builder b = JvmThreadStats.newBuilder();
        try {
            b.setCurrent(threadMX.getThreadCount())
             .setDaemon(threadMX.getDaemonThreadCount())
             .setPeak(threadMX.getPeakThreadCount())
             .setTotalStarted((int) threadMX.getTotalStartedThreadCount());

            long[] deadlocked = threadMX.findDeadlockedThreads();
            b.setDeadlocked(deadlocked != null ? deadlocked.length : 0);

            // Depth 0: fetch state only, no stack traces (cheap)
            ThreadInfo[] infos = threadMX.getThreadInfo(threadMX.getAllThreadIds(), 0);
            int runnable = 0, blocked = 0, waiting = 0, timedWaiting = 0;
            for (ThreadInfo info : infos) {
                if (info == null) continue;  // thread terminated between getAllThreadIds and getThreadInfo
                switch (info.getThreadState()) {
                    case RUNNABLE      -> runnable++;
                    case BLOCKED       -> blocked++;
                    case WAITING       -> waiting++;
                    case TIMED_WAITING -> timedWaiting++;
                    default            -> { /* NEW, TERMINATED — not in live count */ }
                }
            }
            b.setRunnable(runnable)
             .setBlocked(blocked)
             .setWaiting(waiting)
             .setTimedWaiting(timedWaiting);

        } catch (Exception e) {
            log.debug("ThreadStats collection partial: {}", e.getMessage());
        }
        return b.build();
    }

    // ── Classes ──────────────────────────────────────────────────────────────

    private JvmClassStats collectClasses() {
        return JvmClassStats.newBuilder()
                .setLoaded(classMX.getLoadedClassCount())
                .setTotalLoaded((int) classMX.getTotalLoadedClassCount())
                .setUnloaded((int) classMX.getUnloadedClassCount())
                .build();
    }

    // ── Buffer pools ─────────────────────────────────────────────────────────

    private List<JvmBufferPool> collectBuffers() {
        List<JvmBufferPool> result = new ArrayList<>();
        for (BufferPoolMXBean pool : bufferMXBeans) {
            result.add(JvmBufferPool.newBuilder()
                    .setName(pool.getName())
                    .setCount((int) pool.getCount())
                    .setUsedBytes(pool.getMemoryUsed())
                    .setTotalCapacityBytes(pool.getTotalCapacity())
                    .build());
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GcImplementation resolveGcImpl() {
        String names = gcMXBeans.stream()
                .map(GarbageCollectorMXBean::getName)
                .collect(Collectors.joining(" "));
        if (names.contains("G1"))         return GcImplementation.GC_IMPL_G1GC;
        if (names.contains("ZGC"))        return GcImplementation.GC_IMPL_ZGC;
        if (names.contains("Shenandoah")) return GcImplementation.GC_IMPL_SHENANDOAH;
        if (names.contains("PS"))         return GcImplementation.GC_IMPL_PARALLEL;
        if (names.contains("Copy"))       return GcImplementation.GC_IMPL_SERIAL;
        return GcImplementation.GC_IMPL_OTHER;
    }

    private static boolean isConcurrent(String name) {
        return name.contains("Concurrent")
            || name.contains("Cycles")
            || name.equals("ZGC");
    }

    private static MemoryPoolType toProtoPoolType(MemoryType type) {
        return type == MemoryType.HEAP
                ? MemoryPoolType.MEMORY_POOL_TYPE_HEAP
                : MemoryPoolType.MEMORY_POOL_TYPE_NON_HEAP;
    }
}
