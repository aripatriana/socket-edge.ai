package id.co.jalin.seconsole.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Engine-side JVM snapshot, wire-compatible with
 * {@code GET /socket/snapshot/jvm} per {@code spec-jvm-snapshot.md}.
 *
 * <p>This is the raw inbound DTO from the engine. The console reshapes
 * it into {@link id.co.jalin.seconsole.dto.response.JvmMetricsDto}
 * (the same shape the console already returns for its own JVM) so the
 * frontend can reuse one set of components for both tabs.
 *
 * <p>Unknown fields are ignored so engine can add new fields without
 * breaking the console.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JvmSnapshot(
        String snapshotId,
        long capturedAt,
        EngineProcess engineProcess,
        MemoryArea heap,
        MemoryArea nonHeap,
        List<Pool> memoryPools,
        List<Gc> gc,
        Threads threads,
        List<ThreadDetail> threadDetails,
        Classes classes,
        List<Buffer> buffers
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EngineProcess(
            long pid,
            long uptime,
            long startTime,
            String javaVersion,
            String vmName,
            String vmVendor
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemoryArea(
            long used,
            long committed,
            long max,
            long init
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pool(
            String name,
            String type,           // "HEAP" | "NON_HEAP"
            long used,
            long committed,
            long max
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gc(
            String name,
            long collectionCount,
            long collectionTimeMs
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Threads(
            int current,
            int daemon,
            int peak,
            long totalStarted,
            int deadlocked,
            ByState byState
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ByState(
                Integer NEW,
                Integer RUNNABLE,
                Integer BLOCKED,
                Integer WAITING,
                Integer TIMED_WAITING,
                Integer TERMINATED
        ) {}
    }

    /** Only present when engine called with {@code ?details=true}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ThreadDetail(
            long id,
            String name,
            String state,
            boolean daemon,
            long cpuTimeNs,
            int stackDepth
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Classes(
            int loaded,
            long totalLoaded,
            long unloaded
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Buffer(
            String name,           // "direct" | "mapped"
            long count,
            long memoryUsed,
            long totalCapacity
    ) {}
}
