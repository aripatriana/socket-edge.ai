package id.co.jalin.seconsole.service;

import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto.CpuMetrics;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto.DiskMetrics;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto.FileDescriptorMetrics;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto.HostInfo;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto.MemoryMetrics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Probes OS + JVM for system-level metrics (CPU, memory, disk, FD, host info).
 *
 * Design notes:
 *   1. OSHI probe is expensive — we hold results for 2 seconds and return the
 *      cached snapshot to any caller within that window. Dashboard polls at 2s
 *      so the cache amortizes cost while giving near-live freshness.
 *   2. CPU percentage requires a DELTA between two ticks. We compute it using
 *      the CentralProcessor.getSystemCpuLoadBetweenTicks API — the first probe
 *      returns ~0% because there's no previous baseline; subsequent ones are
 *      accurate.
 *   3. Load average is Linux-only (null on Windows). OSHI returns NaN on
 *      unsupported platforms; we normalize to null for clean JSON output.
 *   4. Open FD / max FD requires UnixOperatingSystemMXBean — reflected so the
 *      class loads on Windows without a link error.
 */
@Service
public class SystemMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsService.class);
    private static final long CACHE_TTL_MS = 2_000;

    private final SystemInfo oshi = new SystemInfo();
    private final HardwareAbstractionLayer hal = oshi.getHardware();
    private final OperatingSystem os = oshi.getOperatingSystem();
    private final CentralProcessor processor = hal.getProcessor();

    /** Previous CPU ticks for delta calculation. */
    private long[] prevTicks = new long[CentralProcessor.TickType.values().length];

    /** Cached snapshot to avoid re-probing OSHI on every request. */
    private final AtomicReference<CachedSnapshot> cache = new AtomicReference<>(null);

    private record CachedSnapshot(long takenAtMillis, SystemMetricsDto dto) {}

    @PostConstruct
    void prime() {
        // Prime the tick baseline so the first real call has a proper delta window.
        prevTicks = processor.getSystemCpuLoadTicks();
        log.info("SystemMetricsService initialized: os={} cores={} hostname={}",
            os.toString(),
            processor.getLogicalProcessorCount(),
            resolveHostname());
    }

    public SystemMetricsDto snapshot() {
        CachedSnapshot current = cache.get();
        long now = System.currentTimeMillis();
        if (current != null && now - current.takenAtMillis() < CACHE_TTL_MS) {
            return current.dto();
        }

        SystemMetricsDto fresh = probe();
        cache.set(new CachedSnapshot(now, fresh));
        return fresh;
    }

    // --- Probe ----------------------------------------------------------------

    private SystemMetricsDto probe() {
        return new SystemMetricsDto(
            Instant.now(),
            readCpu(),
            readMemory(),
            readDisks(),
            readFileDescriptors(),
            readSystemInfo()
        );
    }

    private CpuMetrics readCpu() {
        long[] ticks = processor.getSystemCpuLoadTicks();
        double systemLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks);
        prevTicks = ticks;

        double processLoad = readProcessCpu();

        double[] loadAvg = processor.getSystemLoadAverage(3);
        Double l1 = sanitize(loadAvg.length > 0 ? loadAvg[0] : -1);
        Double l5 = sanitize(loadAvg.length > 1 ? loadAvg[1] : -1);
        Double l15 = sanitize(loadAvg.length > 2 ? loadAvg[2] : -1);

        return new CpuMetrics(
            percent(processLoad),
            percent(systemLoad),
            l1,
            l5,
            l15,
            processor.getLogicalProcessorCount(),
            processor.getPhysicalProcessorCount(),
            processor.getProcessorIdentifier().getName()
        );
    }

    private double readProcessCpu() {
        // Use OSHI's OSProcess API instead of reflecting to com.sun.management.*
        // — avoids JPMS encapsulation issues on newer JVMs and works identically
        // across Windows/Linux/Mac.
        //
        // getProcessCpuLoadCumulative returns cumulative CPU time / elapsed time,
        // which is the same semantic as JVM's getProcessCpuLoad. Normalized to
        // the total logical CPU count so "100%" means "using one full core".
        try {
            int pid = os.getProcessId();
            var process = os.getProcess(pid);
            if (process == null) return Double.NaN;
            double cumulative = process.getProcessCpuLoadCumulative();
            // Normalize: OSHI returns fraction across all cores. If a single-
            // threaded process pegs one core on a 12-core box, cumulative is
            // roughly 1/12 = 0.083. Multiply by core count to match "% of one
            // core" convention, then clamp to 0..1 for display purposes. We
            // leave it as fraction-of-all-cores (matching systemCpuPercent
            // semantics) — that's more useful at the dashboard level.
            return cumulative;
        } catch (Throwable t) {
            log.debug("Process CPU unavailable: {}", t.getMessage());
            return Double.NaN;
        }
    }

    private MemoryMetrics readMemory() {
        GlobalMemory mem = hal.getMemory();
        long total = mem.getTotal();
        long available = mem.getAvailable();
        long used = total - available;
        Double usedPct = total > 0 ? percent((double) used / total) : null;

        long swapTotal = mem.getVirtualMemory().getSwapTotal();
        long swapUsed = mem.getVirtualMemory().getSwapUsed();

        return new MemoryMetrics(total, available, used, usedPct, swapTotal, swapUsed);
    }

    private List<DiskMetrics> readDisks() {
        FileSystem fs = os.getFileSystem();
        List<OSFileStore> stores = fs.getFileStores();
        List<DiskMetrics> out = new ArrayList<>(stores.size());

        for (OSFileStore s : stores) {
            long total = s.getTotalSpace();
            long usable = s.getUsableSpace();
            long used = total - usable;
            Double usedPct = total > 0 ? percent((double) used / total) : null;

            // Skip pseudo-filesystems (total == 0 is a strong signal for these).
            if (total <= 0) continue;

            String name = s.getLabel();
            if (name == null || name.isBlank()) {
                name = s.getMount();
            }

            out.add(new DiskMetrics(
                name,
                s.getMount(),
                s.getType(),
                total,
                usable,
                used,
                usedPct
            ));
        }
        return out;
    }

    private FileDescriptorMetrics readFileDescriptors() {
        try {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            // UnixOperatingSystemMXBean — method resolution via reflection for portability.
            var openM = tryMethod(bean, "getOpenFileDescriptorCount");
            var maxM = tryMethod(bean, "getMaxFileDescriptorCount");
            if (openM == null || maxM == null) {
                return new FileDescriptorMetrics(null, null, null);
            }
            long open = (long) openM.invoke(bean);
            long max = (long) maxM.invoke(bean);
            Double pct = max > 0 ? percent((double) open / max) : null;
            return new FileDescriptorMetrics(open, max, pct);
        } catch (Throwable t) {
            log.debug("File descriptor metrics unavailable: {}", t.getMessage());
            return new FileDescriptorMetrics(null, null, null);
        }
    }

    private static java.lang.reflect.Method tryMethod(Object target, String name) {
        try {
            var m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private HostInfo readSystemInfo() {
        long uptimeSec = os.getSystemUptime();
        Instant boot = Instant.ofEpochSecond(os.getSystemBootTime());
        return new HostInfo(
            resolveHostname(),
            os.getFamily(),
            os.getVersionInfo() != null ? os.getVersionInfo().toString() : System.getProperty("os.version"),
            System.getProperty("os.arch"),
            uptimeSec,
            boot
        );
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String env = System.getenv("HOSTNAME");
            return env != null ? env : "unknown";
        }
    }

    // --- Helpers --------------------------------------------------------------

    /** Returns null for NaN/negative, otherwise rounds to 1 decimal place. */
    private static Double percent(double fraction) {
        if (Double.isNaN(fraction) || fraction < 0) return null;
        return Math.round(fraction * 1000.0) / 10.0;
    }

    private static Double sanitize(double v) {
        if (Double.isNaN(v) || v < 0) return null;
        return Math.round(v * 100.0) / 100.0;
    }
}
