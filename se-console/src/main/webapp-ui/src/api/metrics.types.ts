// Types matching the backend SystemMetricsDto shape.
// Kept as one file so the contract is easy to find.

export interface SystemMetrics {
  timestamp: string; // ISO-8601
  cpu: CpuMetrics;
  memory: MemoryMetrics;
  disks: DiskMetrics[];
  fileDescriptors: FileDescriptorMetrics;
  host: HostInfo;
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
  uptimeSeconds: number | null;
  bootTime: string | null;
}
