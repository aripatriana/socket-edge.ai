package id.co.jalin.seconsole.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * JVM-level metrics snapshot from the console's own JVM (not the engine).
 * Maps to the Monitoring → JVM tab per Foundation 6.1.
 */
public record JvmMetricsDto(
        Instant timestamp,
        MemoryArea heap,
        MemoryArea nonHeap,
        List<MemoryPool> pools,
        List<GcCollector> gc,
        ThreadMetrics threads,
        ClassMetrics classes,
        RuntimeInfo runtime,
        DeadlockInfo deadlock
) {

    public record MemoryArea(
            long usedBytes,
            long committedBytes,
            long maxBytes,
            long initBytes,
            Double usedPercent
    ) {}

    public record MemoryPool(
            String name,
            String type,
            long usedBytes,
            long committedBytes,
            long maxBytes,
            Double usedPercent
    ) {}

    public record GcCollector(
            String name,
            long collectionCount,
            long collectionTimeMs,
            List<String> poolNames
    ) {}

    public record ThreadMetrics(
            int liveCount,
            int daemonCount,
            int peakCount,
            long totalStartedCount,
            Integer blockedCount,
            Integer waitingCount,
            Integer timedWaitingCount,
            Integer runnableCount
    ) {}

    public record ClassMetrics(
            int loadedCount,
            long totalLoadedCount,
            long unloadedCount
    ) {}

    public record RuntimeInfo(
            String vmName,
            String vmVendor,
            String vmVersion,
            String specVersion,
            long uptimeMillis,
            long startTime,
            List<String> inputArguments
    ) {}

    /**
     * Deadlock detection summary. When `count == 0`, no threads are deadlocked
     * and `threads` is empty. When non-zero, each entry lists a deadlocked
     * thread along with the lock it's waiting on and who holds that lock.
     *
     * Semantics per ThreadMXBean.findDeadlockedThreads(): returns only cycles
     * involving object monitors AND ownable synchronizers. For monitor-only
     * cycles, use findMonitorDeadlockedThreads — we cover both here.
     */
    public record DeadlockInfo(
            int count,
            List<DeadlockedThread> threads
    ) {}

    public record DeadlockedThread(
            long threadId,
            String threadName,
            String threadState,        // e.g. "BLOCKED", "WAITING"
            String lockName,           // what this thread is waiting on
            Long lockOwnerId,          // who holds the lock (may be null)
            String lockOwnerName,      // name of owner thread (may be null)
            List<String> stackTrace    // formatted frames (max 12 for banner preview)
    ) {}
}
