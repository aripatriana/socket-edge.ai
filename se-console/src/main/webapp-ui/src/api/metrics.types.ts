export interface SystemMetrics {
  timestamp: string;
  cpu: CpuMetrics;
  memory: MemoryMetrics;
  disks: DiskMetrics[];
  fileDescriptors: FileDescriptorMetrics;
  host: HostInfo;
  process: ProcessMetrics;
}

export interface CpuMetrics {
  processCpuPercent: number | null;
  systemCpuPercent: number | null;
  loadAvg1m: number | null;
  loadAvg5m: number | null;
  loadAvg15m: number | null;
  logicalCores: number;
  physicalCores: number;
  model: string;
}

export interface MemoryMetrics {
  totalBytes: number;
  availableBytes: number;
  usedBytes: number;
  usedPercent: number | null;
  swapTotalBytes: number;
  swapUsedBytes: number;
  cachedBytes: number | null;      // Linux only
  buffersBytes: number | null;     // Linux only
}

export interface DiskMetrics {
  name: string;
  mount: string;
  fsType: string;
  totalBytes: number;
  usableBytes: number;
  usedBytes: number;
  usedPercent: number | null;
}

export interface FileDescriptorMetrics {
  open: number | null;
  max: number | null;
  usedPercent: number | null;
}

export interface HostInfo {
  hostname: string;
  osName: string;
  osVersion: string;
  osArch: string;
  uptimeSeconds: number;
  bootTime: string;
}

/**
 * Per-process info for the SE-Console JVM. Most fields nullable — OSHI
 * may not resolve on rare occasions and platform coverage varies.
 */
export interface ProcessMetrics {
  pid: number | null;
  processName: string | null;
  user: string | null;
  workingDirectory: string | null;
  startTime: string | null;        // ISO-8601 when present
  uptimeSeconds: number | null;
  residentSetSizeBytes: number | null;
  virtualMemorySizeBytes: number | null;
  threadCount: number | null;
  openFileCount: number | null;
}

/**
 * One row from /api/console/system/history — maps directly to
 * ConsoleSystemSnapshotEntity. Flat column shape (not the nested DTO),
 * because this is what the DB stores and what chart code wants to
 * iterate over. `disksJson` is a JSON-serialized DiskMetrics[] for
 * drill-down views; parse on demand.
 */
export interface SystemSnapshotRow {
  id: number;
  capturedAt: string;

  cpuSystemPct: number | null;
  cpuProcessPct: number | null;
  loadAvg1m: number | null;
  loadAvg5m: number | null;
  loadAvg15m: number | null;
  cpuLogicalCores: number | null;
  cpuPhysicalCores: number | null;
  cpuModel: string | null;

  memTotalBytes: number;
  memAvailableBytes: number;
  memUsedBytes: number;
  memUsedPct: number | null;
  swapTotalBytes: number;
  swapUsedBytes: number;
  memCachedBytes: number | null;
  memBuffersBytes: number | null;

  fdOpen: number | null;
  fdMax: number | null;
  fdUsedPct: number | null;

  hostName: string | null;
  osName: string | null;
  osVersion: string | null;
  osArch: string | null;
  hostUptimeSeconds: number | null;
  hostBootTime: string | null;

  processPid: number | null;
  processName: string | null;
  processUser: string | null;
  processWorkingDir: string | null;
  processStartTime: string | null;
  processUptimeSeconds: number | null;
  processRssBytes: number | null;
  processVmemBytes: number | null;
  processThreadCount: number | null;
  processOpenFiles: number | null;

  disksJson: string | null;
}
