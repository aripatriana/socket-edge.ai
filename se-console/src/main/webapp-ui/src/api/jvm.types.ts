export interface JvmMetrics {
  timestamp: string;
  heap: MemoryArea;
  nonHeap: MemoryArea;
  pools: MemoryPool[];
  gc: GcCollector[];
  threads: ThreadMetrics;
  classes: ClassMetrics;
  runtime: RuntimeInfo;
  deadlock: DeadlockInfo;
}

export interface MemoryArea {
  usedBytes: number;
  committedBytes: number;
  maxBytes: number;
  initBytes: number;
  usedPercent: number | null;
}

export interface MemoryPool {
  name: string;
  type: 'HEAP' | 'NON_HEAP';
  usedBytes: number;
  committedBytes: number;
  maxBytes: number;
  usedPercent: number | null;
}

export interface GcCollector {
  name: string;
  collectionCount: number;
  collectionTimeMs: number;
  poolNames: string[];
}

export interface ThreadMetrics {
  liveCount: number;
  daemonCount: number;
  peakCount: number;
  totalStartedCount: number;
  blockedCount: number | null;
  waitingCount: number | null;
  timedWaitingCount: number | null;
  runnableCount: number | null;
}

export interface ClassMetrics {
  loadedCount: number;
  totalLoadedCount: number;
  unloadedCount: number;
}

export interface RuntimeInfo {
  vmName: string;
  vmVendor: string;
  vmVersion: string;
  specVersion: string;
  uptimeMillis: number;
  startTime: number;
  inputArguments: string[];
}

/** Deadlock detection result. `count === 0` means no deadlock. */
export interface DeadlockInfo {
  count: number;
  threads: DeadlockedThread[];
}

export interface DeadlockedThread {
  threadId: number;
  threadName: string;
  threadState: string;
  lockName: string | null;
  lockOwnerId: number | null;
  lockOwnerName: string | null;
  stackTrace: string[];
}

// --- /api/console/jvm/threads response -----------------------------------

export interface ThreadList {
  timestamp: string;
  totalCount: number;
  threads: ThreadSummary[];
}

export interface ThreadSummary {
  id: number;
  name: string;
  state: 'NEW' | 'RUNNABLE' | 'BLOCKED' | 'WAITING' | 'TIMED_WAITING' | 'TERMINATED';
  daemon: boolean;
  priority: number;
  cpuTimeNs: number | null;
  userTimeNs: number | null;
  lockName: string | null;
  lockOwnerId: number | null;
  lockOwnerName: string | null;
  stackDepth: number;
  topFrames: string[];
}

/**
 * One row from /api/console/jvm/history — maps directly to
 * ConsoleJvmInternalSnapshotEntity. Same column shape as the engine's
 * JVM history rows (EngineJvmSnapshotEntity), deliberately, so the chart
 * components can consume either source.
 */
export interface JvmSnapshotRow {
  id: number;
  capturedAt: string;

  vmName: string | null;
  vmVendor: string | null;
  vmVersion: string | null;
  specVersion: string | null;
  uptimeMs: number;
  startTime: number;

  heapUsed: number;
  heapCommitted: number;
  heapMax: number;
  heapInit: number;

  nonheapUsed: number;
  nonheapCommitted: number;
  nonheapMax: number;
  nonheapInit: number;

  threadsLive: number;
  threadsDaemon: number;
  threadsPeak: number;
  threadsTotalStarted: number;
  threadsRunnable: number | null;
  threadsBlocked: number | null;
  threadsWaiting: number | null;
  threadsTimedWaiting: number | null;

  classesLoaded: number;
  classesTotalLoaded: number;
  classesUnloaded: number;

  deadlockCount: number;

  gcTotalCount: number;
  gcTotalTimeMs: number;

  poolsJson: string | null;
  gcJson: string | null;
  deadlockJson: string | null;
}

/**
 * One row from /api/engine/jvm/history — maps to EngineJvmSnapshotEntity
 * (engine_jvm_snapshot table). Similar to JvmSnapshotRow but the engine
 * entity has a few different fields (snapshotId, pid, javaVersion) and
 * lacks deadlockCount since the engine doesn't expose it.
 *
 * Shared chart components don't care about the deltas — they access
 * heapUsed/heapMax/gcTotalCount which are present in both.
 */
export interface EngineJvmSnapshotRow {
  id: number;
  capturedAt: string;
  snapshotId: string | null;

  pid: number | null;
  uptimeMs: number;
  javaVersion: string | null;
  vmName: string | null;

  heapUsed: number;
  heapCommitted: number;
  heapMax: number;
  heapInit: number;

  nonheapUsed: number;
  nonheapCommitted: number;
  nonheapMax: number;
  nonheapInit: number;

  threadsCurrent: number;
  threadsDaemon: number;
  threadsPeak: number;
  threadsTotalStarted: number;
  threadsDeadlocked: number;
  threadsRunnable: number | null;
  threadsBlocked: number | null;
  threadsWaiting: number | null;
  threadsTimedWaiting: number | null;

  classesLoaded: number;
  classesTotalLoaded: number;
  classesUnloaded: number;

  gcTotalCount: number;
  gcTotalTimeMs: number;

  poolsJson: string | null;
  gcJson: string | null;
  buffersJson: string | null;
}
