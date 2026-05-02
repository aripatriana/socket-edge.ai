package id.co.jalin.seconsole.grpc;

import com.socket.edge.grpc.*;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Maps se-core gRPC types (OsSnapshot + SystemInfo) to the existing SystemMetricsDto
 * used by MetricsController and the React dashboard.
 *
 * Keeps MetricsController and all frontend contracts unchanged.
 */
@Component
public class OsSnapshotMapper {

    /**
     * Maps a MetricsBundle + cached SystemInfo to SystemMetricsDto.
     *
     * @param bundle     latest MetricsBundle from se-core (contains OsSnapshot)
     * @param systemInfo static system info fetched once from se-core
     */
    public SystemMetricsDto toDto(MetricsBundle bundle, SystemInfo systemInfo) {
        OsSnapshot os = bundle.getOs();

        return new SystemMetricsDto(
                Instant.ofEpochMilli(os.getHeader().getCapturedAt()),
                mapCpu(os, systemInfo),
                mapMemory(os),
                mapDisks(os),
                mapFileDescriptors(os),
                mapHostInfo(os, systemInfo)
        );
    }

    // ── CPU ──────────────────────────────────────────────────────────────────

    private CpuMetrics mapCpu(OsSnapshot os, SystemInfo si) {
        CpuStats cpu   = os.getCpu();
        LoadAverage la = os.getLoadAvg();

        // Proto avg_1m → getAvg1M (protobuf capitalizes letter after digit)
        Double l1  = la.getAvailable() && la.getAvg1M()  >= 0 ? la.getAvg1M()  : null;
        Double l5  = la.getAvailable() && la.getAvg5M()  >= 0 ? la.getAvg5M()  : null;
        Double l15 = la.getAvailable() && la.getAvg15M() >= 0 ? la.getAvg15M() : null;

        return new CpuMetrics(
                nullIfZero(os.getProcess().getProcessCpuPct()),
                nullIfZero(cpu.getSystemPct()),
                l1, l5, l15,
                si != null ? si.getCpuLogical()   : 0,
                si != null ? si.getCpuPhysical()  : 0,
                si != null ? si.getCpuModel()     : "unknown"
        );
    }

    // ── Memory ───────────────────────────────────────────────────────────────

    private MemoryMetrics mapMemory(OsSnapshot os) {
        MemoryStats m = os.getMemory();
        long total = m.getTotalBytes();
        long avail = m.getAvailableBytes();
        long used  = m.getUsedBytes();
        Double pct = total > 0 ? round((double) used / total * 100.0) : null;

        return new MemoryMetrics(
                total, avail, used, pct,
                m.getSwapTotalBytes(),
                m.getSwapUsedBytes()
        );
    }

    // ── Disks ─────────────────────────────────────────────────────────────────

    private List<DiskMetrics> mapDisks(OsSnapshot os) {
        return os.getDisksList().stream()
                .filter(d -> d.getTotalBytes() > 0)
                .map(d -> new DiskMetrics(
                        d.getMount(),                     // name fallback to mount
                        d.getMount(),
                        d.getFsType(),
                        d.getTotalBytes(),
                        d.getFreeBytes(),                 // usableBytes ≈ freeBytes
                        d.getUsedBytes(),
                        d.getUsedPct() > 0 ? d.getUsedPct() : null
                ))
                .toList();
    }

    // ── File Descriptors ─────────────────────────────────────────────────────

    private FileDescriptorMetrics mapFileDescriptors(OsSnapshot os) {
        FileDescriptors fd = os.getFileDesc();
        if (fd.getOpenCount() == 0 && fd.getMaxCount() == 0) {
            return new FileDescriptorMetrics(null, null, null);
        }
        long open = fd.getOpenCount();
        long max  = fd.getMaxCount();
        Double pct = max > 0 ? round((double) open / max * 100.0) : null;
        return new FileDescriptorMetrics(open, max, pct);
    }

    // ── Host Info ─────────────────────────────────────────────────────────────

    private HostInfo mapHostInfo(OsSnapshot os, SystemInfo si) {
        if (si == null) {
            return new HostInfo("unknown", "unknown", "unknown", "unknown", null, null);
        }
        long bootTimeMs = si.getBootTime();
        long uptimeSec  = bootTimeMs > 0
                ? (System.currentTimeMillis() - bootTimeMs) / 1000
                : null != os.getProcess() ? os.getProcess().getUptimeMs() / 1000 : 0;

        return new HostInfo(
                si.getHostname(),
                si.getOsName(),
                si.getOsVersion(),
                si.getArchitecture(),
                uptimeSec,
                bootTimeMs > 0 ? Instant.ofEpochMilli(bootTimeMs) : null
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Double nullIfZero(double v) {
        return v == 0.0 ? null : round(v);
    }

    private static Double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return null;
        return Math.round(v * 10.0) / 10.0;
    }
}