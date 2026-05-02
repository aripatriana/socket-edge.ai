package com.socket.edge.grpc.os;

import com.socket.edge.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.*;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects dynamic OS metrics via OSHI on each call.
 *
 * Stateful: maintains previous tick snapshots for delta-based rate calculations
 * (CPU %, network bps). Must be called from a single thread or with external sync.
 */
public class OsMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(OsMetricsCollector.class);

    private final oshi.SystemInfo        oshi;
    private final HardwareAbstractionLayer hal;
    private final OperatingSystem         os;
    private final CentralProcessor        cpu;

    // CPU delta state
    private long[]   prevSystemTicks    = new long[CentralProcessor.TickType.values().length];
    private long[][] prevProcessorTicks = new long[0][];
    private long     prevCollectMs      = System.currentTimeMillis();

    // Network delta state — keyed by interface name
    private final Map<String, Long> prevNetRx = new HashMap<>();
    private final Map<String, Long> prevNetTx = new HashMap<>();

    public OsMetricsCollector(oshi.SystemInfo oshi) {
        this.oshi = oshi;
        this.hal  = oshi.getHardware();
        this.os   = oshi.getOperatingSystem();
        this.cpu  = hal.getProcessor();

        // Prime CPU baselines so first reading is meaningful
        prevSystemTicks    = cpu.getSystemCpuLoadTicks();
        prevProcessorTicks = cpu.getProcessorCpuLoadTicks();
    }

    /** Collect a full OsSnapshot. Safe to call repeatedly at any interval. */
    public OsSnapshot collect(String snapshotId) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(1, now - prevCollectMs);

        SnapshotHeader header = SnapshotHeader.newBuilder()
                .setSnapshotId(snapshotId)
                .setCapturedAt(now)
                .build();

        OsSnapshot.Builder snap = OsSnapshot.newBuilder()
                .setHeader(header)
                .setProcess(collectProcess())
                .setCpu(collectCpu())
                .setMemory(collectMemory())
                .addAllDisks(collectDisks())
                .addAllNetworks(collectNetworks(elapsed))
                .setLoadAvg(collectLoadAvg())
                .setFileDesc(collectFileDescriptors());

        prevCollectMs = now;
        return snap.build();
    }

    // ── Process ──────────────────────────────────────────────────────────────

    private ProcessInfo collectProcess() {
        ProcessInfo.Builder b = ProcessInfo.newBuilder();
        try {
            int pid = os.getProcessId();
            OSProcess proc = os.getProcess(pid);
            if (proc == null) return b.build();

            b.setPid(pid)
             .setProcessName(proc.getName())
             .setUser(proc.getUser())
             .setWorkingDir(proc.getCurrentWorkingDirectory())
             .setStartTime(proc.getStartTime())
             .setUptimeMs(System.currentTimeMillis() - proc.getStartTime())
             .setRssBytes(proc.getResidentSetSize())
             .setVirtualBytes(proc.getVirtualSize())
             .setProcessCpuPct(sanitize(proc.getProcessCpuLoadCumulative() * 100.0))
             .setThreadCount(proc.getThreadCount())
             .setBytesRead(proc.getBytesRead())
             .setBytesWritten(proc.getBytesWritten());

            // Open files — platform-specific (via FileSystem, not directly on OS)
            b.setOpenFiles((int) os.getFileSystem().getOpenFileDescriptors());

        } catch (Exception e) {
            log.debug("ProcessInfo collection partial: {}", e.getMessage());
        }
        return b.build();
    }

    // ── CPU ──────────────────────────────────────────────────────────────────

    private CpuStats collectCpu() {
        long[] currentSystemTicks    = cpu.getSystemCpuLoadTicks();
        long[][] currentProcessorTicks = cpu.getProcessorCpuLoadTicks();

        double systemPct = cpu.getSystemCpuLoadBetweenTicks(prevSystemTicks) * 100.0;

        // Per-core breakdown
        double[] perCore = cpu.getProcessorCpuLoadBetweenTicks(prevProcessorTicks);

        prevSystemTicks    = currentSystemTicks;
        prevProcessorTicks = currentProcessorTicks;

        // Current frequency (average across cores in MHz)
        long[] freqs = cpu.getCurrentFreq();
        long currentFreqMhz = 0;
        if (freqs != null && freqs.length > 0) {
            long sum = 0;
            for (long f : freqs) sum += f;
            currentFreqMhz = sum / freqs.length / 1_000_000;
        }

        // Decompose system ticks into user/sys/idle
        long[] ticks = currentSystemTicks;
        long user  = ticks[CentralProcessor.TickType.USER.getIndex()];
        long sys   = ticks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long idle  = ticks[CentralProcessor.TickType.IDLE.getIndex()];
        long total = user + sys + idle
                + ticks[CentralProcessor.TickType.NICE.getIndex()]
                + ticks[CentralProcessor.TickType.IOWAIT.getIndex()]
                + ticks[CentralProcessor.TickType.IRQ.getIndex()]
                + ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()];

        double userPct  = total > 0 ? sanitize((double) user / total * 100.0) : 0;
        double sysPct   = total > 0 ? sanitize((double) sys  / total * 100.0) : 0;
        double idlePct  = total > 0 ? sanitize((double) idle / total * 100.0) : 0;

        CpuStats.Builder b = CpuStats.newBuilder()
                .setSystemPct(sanitize(systemPct))
                .setUserPct(userPct)
                .setSysPct(sysPct)
                .setIdlePct(idlePct)
                .setCurrentFreqMhz(currentFreqMhz);

        if (perCore != null) {
            for (double c : perCore) b.addPerCorePct(sanitize(c * 100.0));
        }

        return b.build();
    }

    // ── Memory ───────────────────────────────────────────────────────────────

    private MemoryStats collectMemory() {
        GlobalMemory mem = hal.getMemory();
        long total     = mem.getTotal();
        long available = mem.getAvailable();
        long used      = total - available;

        long swapTotal = mem.getVirtualMemory().getSwapTotal();
        long swapUsed  = mem.getVirtualMemory().getSwapUsed();
        long swapFree  = Math.max(0, swapTotal - swapUsed);

        return MemoryStats.newBuilder()
                .setUsedBytes(used)
                .setFreeBytes(available)
                .setTotalBytes(total)
                .setAvailableBytes(available)  // Linux: available ≠ free; Windows: same
                .setSwapUsedBytes(swapUsed)
                .setSwapFreeBytes(swapFree)
                .setSwapTotalBytes(swapTotal)
                .build();
    }

    // ── Disks ─────────────────────────────────────────────────────────────────

    private List<DiskStats> collectDisks() {
        FileSystem fs = os.getFileSystem();
        List<OSFileStore> stores = fs.getFileStores(true); // refresh=true
        List<DiskStats> result = new ArrayList<>();

        for (OSFileStore s : stores) {
            long total = s.getTotalSpace();
            if (total <= 0) continue;  // skip pseudo-filesystems

            long free  = s.getUsableSpace();
            long used  = total - free;
            double pct = sanitize((double) used / total * 100.0);

            result.add(DiskStats.newBuilder()
                    .setMount(s.getMount())
                    .setFsType(s.getType() != null ? s.getType() : "")
                    .setUsedBytes(used)
                    .setFreeBytes(free)
                    .setTotalBytes(total)
                    .setUsedPct(pct)
                    // read_bps / write_bps: requires HWDiskStore correlation — Phase 2
                    .build());
        }
        return result;
    }

    // ── Network ──────────────────────────────────────────────────────────────

    private List<NetworkInterface> collectNetworks(long elapsedMs) {
        List<NetworkIF> nics = hal.getNetworkIFs(true); // refresh=true
        List<NetworkInterface> result = new ArrayList<>();

        for (NetworkIF nic : nics) {
            String name = nic.getName();
            long rx = nic.getBytesRecv();
            long tx = nic.getBytesSent();

            long prevRx = prevNetRx.getOrDefault(name, rx);
            long prevTx = prevNetTx.getOrDefault(name, tx);

            long rxBps = elapsedMs > 0 ? (rx - prevRx) * 1000L / elapsedMs : 0;
            long txBps = elapsedMs > 0 ? (tx - prevTx) * 1000L / elapsedMs : 0;

            prevNetRx.put(name, rx);
            prevNetTx.put(name, tx);

            // Determine loopback: name starts with "lo" or all IPs are loopback
            boolean loopback = name.startsWith("lo") || name.equalsIgnoreCase("loopback");

            NetworkInterface.Builder b = NetworkInterface.newBuilder()
                    .setName(name)
                    .setDisplayName(nic.getDisplayName() != null ? nic.getDisplayName() : name)
                    .setMacAddress(nic.getMacaddr() != null ? nic.getMacaddr() : "")
                    .setRxBytes(rx)
                    .setTxBytes(tx)
                    .setRxBps(Math.max(0, rxBps))
                    .setTxBps(Math.max(0, txBps))
                    .setRxPackets(nic.getPacketsRecv())
                    .setTxPackets(nic.getPacketsSent())
                    .setRxErrors(nic.getInErrors())
                    .setTxErrors(nic.getOutErrors())
                    .setSpeedMbps((int) (nic.getSpeed() / 1_000_000))
                    .setIsLoopback(loopback);

            String[] ipv4 = nic.getIPv4addr();
            String[] ipv6 = nic.getIPv6addr();
            if (ipv4 != null) for (String ip : ipv4) b.addIpAddresses(ip);
            if (ipv6 != null) for (String ip : ipv6) b.addIpAddresses(ip);

            result.add(b.build());
        }
        return result;
    }

    // ── Load Average ─────────────────────────────────────────────────────────

    private LoadAverage collectLoadAvg() {
        double[] avg = cpu.getSystemLoadAverage(3);
        boolean available = avg != null && avg.length > 0 && avg[0] >= 0;

        double a1  = available && avg.length > 0 ? sanitize(avg[0]) : 0.0;
        double a5  = available && avg.length > 1 ? sanitize(avg[1]) : 0.0;
        double a15 = available && avg.length > 2 ? sanitize(avg[2]) : 0.0;

        // Proto: avg_1m → setAvg1M (protobuf capitalizes letter after digit)
        return LoadAverage.newBuilder()
                .setAvg1M(a1)
                .setAvg5M(a5)
                .setAvg15M(a15)
                .setAvailable(available)
                .build();
    }

    // ── File Descriptors ─────────────────────────────────────────────────────

    private FileDescriptors collectFileDescriptors() {
        try {
            long open = os.getFileSystem().getOpenFileDescriptors();
            long max  = reflectMaxFd();
            return FileDescriptors.newBuilder()
                    .setOpenCount(open)
                    .setMaxCount(max)
                    .build();
        } catch (Exception e) {
            log.debug("FileDescriptors unavailable: {}", e.getMessage());
            return FileDescriptors.getDefaultInstance();
        }
    }

    /** Reads max FD via reflection — Linux/macOS only, returns 0 on Windows. */
    private long reflectMaxFd() {
        try {
            Object bean = ManagementFactory.getOperatingSystemMXBean();
            Method m = bean.getClass().getMethod("getMaxFileDescriptorCount");
            m.setAccessible(true);
            return (long) m.invoke(bean);
        } catch (Exception e) {
            return 0L;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static double sanitize(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return 0.0;
        return Math.round(v * 100.0) / 100.0;
    }
}