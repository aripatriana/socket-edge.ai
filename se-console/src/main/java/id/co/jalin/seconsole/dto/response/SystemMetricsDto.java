package id.co.jalin.seconsole.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * System-level metrics snapshot. Sources: OSHI + JVM standard MXBean.
 * Per Foundation 6.1, maps to Monitoring → System tab (and contributes
 * to Dashboard landing page).
 *
 * Extended in Chat 3d with:
 *  - MemoryMetrics: cachedBytes, buffersBytes (Linux /proc/meminfo; null elsewhere)
 *  - ProcessMetrics: PID, user, start time, RSS, virtual memory, working dir
 */
public record SystemMetricsDto(
        Instant timestamp,
        CpuMetrics cpu,
        MemoryMetrics memory,
        List<DiskMetrics> disks,
        FileDescriptorMetrics fileDescriptors,
        HostInfo host,
        ProcessMetrics process
) {

    public record CpuMetrics(
            Double processCpuPercent,
            Double systemCpuPercent,
            Double loadAvg1m,
            Double loadAvg5m,
            Double loadAvg15m,
            int logicalCores,
            int physicalCores,
            String model
    ) {}

    public record MemoryMetrics(
            long totalBytes,
            long availableBytes,
            long usedBytes,
            Double usedPercent,
            long swapTotalBytes,
            long swapUsedBytes,
            Long cachedBytes,         // Linux /proc/meminfo; null on Win/Mac
            Long buffersBytes         // Linux /proc/meminfo; null on Win/Mac
    ) {}

    public record DiskMetrics(
            String name,
            String mount,
            String fsType,
            long totalBytes,
            long usableBytes,
            long usedBytes,
            Double usedPercent
    ) {}

    public record FileDescriptorMetrics(
            Long open,
            Long max,
            Double usedPercent
    ) {}

    public record HostInfo(
            String hostname,
            String osName,
            String osVersion,
            String osArch,
            long uptimeSeconds,
            Instant bootTime
    ) {}

    /**
     * Per-process info for the SE-Console JVM itself.
     * Complements CpuMetrics.processCpuPercent / JvmMetrics heap info.
     *
     * Fields may be null when OSHI can't resolve the own process (very rare)
     * or when the platform doesn't expose them.
     */
    public record ProcessMetrics(
            Integer pid,
            String processName,
            String user,
            String workingDirectory,
            Instant startTime,
            Long uptimeSeconds,
            Long residentSetSizeBytes,   // RSS — physical memory used by process
            Long virtualMemorySizeBytes, // virtual memory
            Integer threadCount,
            Long openFileCount           // may differ from FileDescriptorMetrics
    ) {}
}
