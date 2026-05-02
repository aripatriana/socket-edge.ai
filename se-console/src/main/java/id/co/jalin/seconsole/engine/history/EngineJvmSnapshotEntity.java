package id.co.jalin.seconsole.engine.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per JVM snapshot poll from the engine. Flattened for easy
 * time-series queries — pools, GCs and buffers are serialized to JSON
 * blobs because their cardinality varies across JVM versions.
 *
 * <p>Retention is capped by {@link EngineJvmHistoryService#prune()} so
 * this table does not grow unbounded.
 */
@Entity
@Table(name = "engine_jvm_snapshot")
public class EngineJvmSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "snapshot_id", length = 64)
    private String snapshotId;

    // --- engineProcess ---
    @Column(name = "pid")
    private Long pid;

    @Column(name = "uptime_ms")
    private long uptimeMs;

    @Column(name = "java_version", length = 64)
    private String javaVersion;

    @Column(name = "vm_name", length = 128)
    private String vmName;

    // --- heap ---
    @Column(name = "heap_used", nullable = false)
    private long heapUsed;

    @Column(name = "heap_committed", nullable = false)
    private long heapCommitted;

    @Column(name = "heap_max", nullable = false)
    private long heapMax;

    @Column(name = "heap_init", nullable = false)
    private long heapInit;

    // --- non-heap ---
    @Column(name = "nonheap_used", nullable = false)
    private long nonheapUsed;

    @Column(name = "nonheap_committed", nullable = false)
    private long nonheapCommitted;

    @Column(name = "nonheap_max", nullable = false)
    private long nonheapMax;

    @Column(name = "nonheap_init", nullable = false)
    private long nonheapInit;

    // --- threads (scalars) ---
    @Column(name = "threads_current", nullable = false)
    private int threadsCurrent;

    @Column(name = "threads_daemon", nullable = false)
    private int threadsDaemon;

    @Column(name = "threads_peak", nullable = false)
    private int threadsPeak;

    @Column(name = "threads_total_started", nullable = false)
    private long threadsTotalStarted;

    @Column(name = "threads_deadlocked", nullable = false)
    private int threadsDeadlocked;

    @Column(name = "threads_runnable")
    private Integer threadsRunnable;

    @Column(name = "threads_blocked")
    private Integer threadsBlocked;

    @Column(name = "threads_waiting")
    private Integer threadsWaiting;

    @Column(name = "threads_timed_waiting")
    private Integer threadsTimedWaiting;

    // --- classes ---
    @Column(name = "classes_loaded", nullable = false)
    private int classesLoaded;

    @Column(name = "classes_total_loaded", nullable = false)
    private long classesTotalLoaded;

    @Column(name = "classes_unloaded", nullable = false)
    private long classesUnloaded;

    // --- GC aggregate ---
    @Column(name = "gc_total_count", nullable = false)
    private long gcTotalCount;

    @Column(name = "gc_total_time_ms", nullable = false)
    private long gcTotalTimeMs;

    // --- JSON blobs (variable cardinality lists) ---
    @Lob
    @Column(name = "pools_json")
    private String poolsJson;

    @Lob
    @Column(name = "gc_json")
    private String gcJson;

    @Lob
    @Column(name = "buffers_json")
    private String buffersJson;

    public EngineJvmSnapshotEntity() {}

    // -------------------------------------------------------------------------
    // getters / setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
    public Long getPid() { return pid; }
    public void setPid(Long pid) { this.pid = pid; }
    public long getUptimeMs() { return uptimeMs; }
    public void setUptimeMs(long uptimeMs) { this.uptimeMs = uptimeMs; }
    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }
    public String getVmName() { return vmName; }
    public void setVmName(String vmName) { this.vmName = vmName; }

    public long getHeapUsed() { return heapUsed; }
    public void setHeapUsed(long heapUsed) { this.heapUsed = heapUsed; }
    public long getHeapCommitted() { return heapCommitted; }
    public void setHeapCommitted(long heapCommitted) { this.heapCommitted = heapCommitted; }
    public long getHeapMax() { return heapMax; }
    public void setHeapMax(long heapMax) { this.heapMax = heapMax; }
    public long getHeapInit() { return heapInit; }
    public void setHeapInit(long heapInit) { this.heapInit = heapInit; }

    public long getNonheapUsed() { return nonheapUsed; }
    public void setNonheapUsed(long nonheapUsed) { this.nonheapUsed = nonheapUsed; }
    public long getNonheapCommitted() { return nonheapCommitted; }
    public void setNonheapCommitted(long nonheapCommitted) { this.nonheapCommitted = nonheapCommitted; }
    public long getNonheapMax() { return nonheapMax; }
    public void setNonheapMax(long nonheapMax) { this.nonheapMax = nonheapMax; }
    public long getNonheapInit() { return nonheapInit; }
    public void setNonheapInit(long nonheapInit) { this.nonheapInit = nonheapInit; }

    public int getThreadsCurrent() { return threadsCurrent; }
    public void setThreadsCurrent(int threadsCurrent) { this.threadsCurrent = threadsCurrent; }
    public int getThreadsDaemon() { return threadsDaemon; }
    public void setThreadsDaemon(int threadsDaemon) { this.threadsDaemon = threadsDaemon; }
    public int getThreadsPeak() { return threadsPeak; }
    public void setThreadsPeak(int threadsPeak) { this.threadsPeak = threadsPeak; }
    public long getThreadsTotalStarted() { return threadsTotalStarted; }
    public void setThreadsTotalStarted(long threadsTotalStarted) { this.threadsTotalStarted = threadsTotalStarted; }
    public int getThreadsDeadlocked() { return threadsDeadlocked; }
    public void setThreadsDeadlocked(int threadsDeadlocked) { this.threadsDeadlocked = threadsDeadlocked; }
    public Integer getThreadsRunnable() { return threadsRunnable; }
    public void setThreadsRunnable(Integer threadsRunnable) { this.threadsRunnable = threadsRunnable; }
    public Integer getThreadsBlocked() { return threadsBlocked; }
    public void setThreadsBlocked(Integer threadsBlocked) { this.threadsBlocked = threadsBlocked; }
    public Integer getThreadsWaiting() { return threadsWaiting; }
    public void setThreadsWaiting(Integer threadsWaiting) { this.threadsWaiting = threadsWaiting; }
    public Integer getThreadsTimedWaiting() { return threadsTimedWaiting; }
    public void setThreadsTimedWaiting(Integer threadsTimedWaiting) { this.threadsTimedWaiting = threadsTimedWaiting; }

    public int getClassesLoaded() { return classesLoaded; }
    public void setClassesLoaded(int classesLoaded) { this.classesLoaded = classesLoaded; }
    public long getClassesTotalLoaded() { return classesTotalLoaded; }
    public void setClassesTotalLoaded(long classesTotalLoaded) { this.classesTotalLoaded = classesTotalLoaded; }
    public long getClassesUnloaded() { return classesUnloaded; }
    public void setClassesUnloaded(long classesUnloaded) { this.classesUnloaded = classesUnloaded; }

    public long getGcTotalCount() { return gcTotalCount; }
    public void setGcTotalCount(long gcTotalCount) { this.gcTotalCount = gcTotalCount; }
    public long getGcTotalTimeMs() { return gcTotalTimeMs; }
    public void setGcTotalTimeMs(long gcTotalTimeMs) { this.gcTotalTimeMs = gcTotalTimeMs; }

    public String getPoolsJson() { return poolsJson; }
    public void setPoolsJson(String poolsJson) { this.poolsJson = poolsJson; }
    public String getGcJson() { return gcJson; }
    public void setGcJson(String gcJson) { this.gcJson = gcJson; }
    public String getBuffersJson() { return buffersJson; }
    public void setBuffersJson(String buffersJson) { this.buffersJson = buffersJson; }
}
