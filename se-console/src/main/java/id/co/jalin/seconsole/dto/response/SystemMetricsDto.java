package id.co.jalin.seconsole.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * System-level metrics snapshot. Returned by GET /api/system/metrics.
 * Shape maps 1:1 to the "Monitoring → System" tab and the Dashboard health strip.
 *
 * Category A per the gap analysis document: all fields are satisfiable without
 * engine JMX — OSHI + standard JVM MXBeans are sufficient.
 *
 * Fields may be null on platforms that don't expose them (e.g. load average on Windows,
 * /proc/* readings on non-Linux). Frontend displays "N/A" for nulls.
 */
public record SystemMetricsDto(
        Instant timestamp,
        CpuMetrics cpu,
        MemoryMetrics memory,
        List<DiskMetrics> disks,
        FileDescriptorMetrics fileDescriptors,
        HostInfo host
) {

    /** CPU usage and load averages. Values are percentages in 0..100. */
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

    /** Physical memory + swap. Sizes in bytes. */
    public record MemoryMetrics(
            long totalBytes,
            long availableBytes,
            long usedBytes,
            Double usedPercent,
            long swapTotalBytes,
            long swapUsedBytes
    ) {}

    /** Per-mount disk usage. */
    public record DiskMetrics(
            String name,           // human label, e.g. "/" or "C:"
            String mount,          // mount point
            String fsType,         // ext4, ntfs, apfs, ...
            long totalBytes,
            long usableBytes,
            long usedBytes,
            Double usedPercent
    ) {}

    /** Unix-only file descriptor counters. Null fields on Windows. */
    public record FileDescriptorMetrics(
            Long open,
            Long max,
            Double usedPercent
    ) {}

    /** Host identity + uptime. Uptime in seconds. */
    public record HostInfo(
            String hostname,
            String osName,
            String osVersion,
            String osArch,
            Long uptimeSeconds,
            Instant bootTime
    ) {}
}
