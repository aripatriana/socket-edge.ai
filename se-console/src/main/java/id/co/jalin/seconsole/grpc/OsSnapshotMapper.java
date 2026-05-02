package id.co.jalin.seconsole.grpc;

import com.socket.edge.grpc.*;
import id.co.jalin.seconsole.dto.response.NetworkMetricsDto;
import id.co.jalin.seconsole.dto.response.NetworkMetricsDto.*;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Maps se-core gRPC types (OsSnapshot + SystemInfo) to SystemMetricsDto and
 * NetworkMetricsDto used by the console's monitoring controllers.
 *
 * Fields not available via gRPC stream:
 *  - MemoryMetrics.cachedBytes / buffersBytes → null  (Linux /proc/meminfo, not in stream)
 *  - NetworkInterfaceInfo.mtu / inDrops / outDrops → 0  (not in proto)
 *  - TcpStateCounts, TcpQualityCounters, listeningPorts → empty  (not in stream)
 */
@Component
public class OsSnapshotMapper {

    // ── System metrics ────────────────────────────────────────────────────────

    public SystemMetricsDto toDto(MetricsBundle bundle, SystemInfo systemInfo) {
        OsSnapshot os = bundle.getOs();
        return new SystemMetricsDto(
                Instant.ofEpochMilli(os.getHeader().getCapturedAt()),
                mapCpu(os, systemInfo),
                mapMemory(os),
                mapDisks(os),
                mapFileDescriptors(os),
                mapHostInfo(os, systemInfo),
                mapProcess(os.getProcess())
        );
    }

    // ── Network metrics ───────────────────────────────────────────────────────

    public NetworkMetricsDto toNetworkDto(MetricsBundle bundle) {
        OsSnapshot os = bundle.getOs();
        return new NetworkMetricsDto(
                Instant.ofEpochMilli(os.getHeader().getCapturedAt()),
                mapInterfaces(os.getNetworksList()),
                emptyTcpStates(),
                emptyTcpQuality(),
                Collections.emptyList()
        );
    }

    // ── CPU ──────────────────────────────────────────────────────────────────

    private CpuMetrics mapCpu(OsSnapshot os, SystemInfo si) {
        CpuStats cpu   = os.getCpu();
        LoadAverage la = os.getLoadAvg();

        Double l1  = la.getAvailable() && la.getAvg1M()  >= 0 ? (double) la.getAvg1M()  : null;
        Double l5  = la.getAvailable() && la.getAvg5M()  >= 0 ? (double) la.getAvg5M()  : null;
        Double l15 = la.getAvailable() && la.getAvg15M() >= 0 ? (double) la.getAvg15M() : null;

        return new CpuMetrics(
                nullIfZero(os.getProcess().getProcessCpuPct()),
                nullIfZero(cpu.getSystemPct()),
                l1, l5, l15,
                si != null ? si.getCpuLogical()  : 0,
                si != null ? si.getCpuPhysical() : 0,
                si != null ? si.getCpuModel()    : "unknown"
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
                m.getSwapUsedBytes(),
                null,   // cachedBytes — Linux /proc/meminfo, not in gRPC stream
                null    // buffersBytes — Linux /proc/meminfo, not in gRPC stream
        );
    }

    // ── Disks ─────────────────────────────────────────────────────────────────

    private List<DiskMetrics> mapDisks(OsSnapshot os) {
        return os.getDisksList().stream()
                .filter(d -> d.getTotalBytes() > 0)
                .map(d -> new DiskMetrics(
                        d.getMount(),
                        d.getMount(),
                        d.getFsType(),
                        d.getTotalBytes(),
                        d.getFreeBytes(),
                        d.getUsedBytes(),
                        d.getUsedPct() > 0 ? (double) d.getUsedPct() : null
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
            return new HostInfo("unknown", "unknown", "unknown", "unknown", 0L, null);
        }
        long bootTimeMs = si.getBootTime();
        long uptimeSec  = bootTimeMs > 0
                ? (System.currentTimeMillis() - bootTimeMs) / 1000
                : os.getProcess().getUptimeMs() / 1000;
        return new HostInfo(
                si.getHostname(),
                si.getOsName(),
                si.getOsVersion(),
                si.getArchitecture(),
                uptimeSec,
                bootTimeMs > 0 ? Instant.ofEpochMilli(bootTimeMs) : null
        );
    }

    // ── Process Info ─────────────────────────────────────────────────────────

    private ProcessMetrics mapProcess(ProcessInfo p) {
        if (p == null || p.getPid() == 0) {
            return new ProcessMetrics(null, null, null, null, null, null, null, null, null, null);
        }
        return new ProcessMetrics(
                (int) p.getPid(),
                emptyToNull(p.getProcessName()),
                emptyToNull(p.getUser()),
                emptyToNull(p.getWorkingDir()),
                p.getStartTime() > 0 ? Instant.ofEpochMilli(p.getStartTime()) : null,
                p.getUptimeMs() > 0  ? p.getUptimeMs() / 1000 : null,
                p.getRssBytes()     > 0 ? p.getRssBytes()     : null,
                p.getVirtualBytes() > 0 ? p.getVirtualBytes() : null,
                p.getThreadCount()  > 0 ? p.getThreadCount()  : null,
                p.getOpenFiles()    > 0 ? (long) p.getOpenFiles() : null
        );
    }

    // ── Network interfaces ────────────────────────────────────────────────────

    private List<NetworkInterfaceInfo> mapInterfaces(List<NetworkInterface> nics) {
        return nics.stream().map(nic -> {
            List<String> ipv4 = nic.getIpAddressesList().stream()
                    .filter(ip -> ip.contains("."))
                    .toList();
            List<String> ipv6 = nic.getIpAddressesList().stream()
                    .filter(ip -> ip.contains(":"))
                    .toList();
            long speedBps = nic.getSpeedMbps() > 0
                    ? (long) nic.getSpeedMbps() * 1_000_000L : -1L;
            boolean up = nic.getRxBytes() > 0 || nic.getTxBytes() > 0 || !nic.getIsLoopback();
            return new NetworkInterfaceInfo(
                    nic.getName(),
                    nic.getDisplayName(),
                    nic.getMacAddress(),
                    ipv4, ipv6,
                    speedBps,
                    0L,     // mtu — not in proto
                    up,
                    nic.getRxBytes(),
                    nic.getTxBytes(),
                    nic.getRxPackets(),
                    nic.getTxPackets(),
                    nic.getRxErrors(),
                    nic.getTxErrors(),
                    0L,     // inDrops — not in proto
                    0L      // outDrops — not in proto
            );
        }).toList();
    }

    // ── TCP empty stubs (not in gRPC stream) ─────────────────────────────────

    private TcpStateCounts emptyTcpStates() {
        return new TcpStateCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Collections.emptyMap());
    }

    private TcpQualityCounters emptyTcpQuality() {
        return new TcpQualityCounters(null, null, null, null, null, null, null, null, null, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Double nullIfZero(double v) {
        return v == 0.0 ? null : round(v);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return null;
        return Math.round(v * 10.0) / 10.0;
    }
}
