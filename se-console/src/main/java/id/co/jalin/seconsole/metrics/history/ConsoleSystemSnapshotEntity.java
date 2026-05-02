package id.co.jalin.seconsole.metrics.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per system-metric poll tick. Mirrors {@code console_system_snapshot}
 * (V4). Flat columns carry scalars we chart directly; disks go into
 * {@code disks_json} because per-mount cardinality varies across deployments.
 *
 * <p>Retention pruned by {@link ConsoleSystemHistoryService#prune()}.
 */
@Entity
@Table(name = "console_system_snapshot")
public class ConsoleSystemSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    // --- CPU ---
    @Column(name = "cpu_system_pct")   private Double cpuSystemPct;
    @Column(name = "cpu_process_pct")  private Double cpuProcessPct;
    @Column(name = "load_avg_1m")      private Double loadAvg1m;
    @Column(name = "load_avg_5m")      private Double loadAvg5m;
    @Column(name = "load_avg_15m")     private Double loadAvg15m;
    @Column(name = "cpu_logical_cores")  private Integer cpuLogicalCores;
    @Column(name = "cpu_physical_cores") private Integer cpuPhysicalCores;
    @Column(name = "cpu_model", length = 256) private String cpuModel;

    // --- Memory ---
    @Column(name = "mem_total_bytes", nullable = false)      private long memTotalBytes;
    @Column(name = "mem_available_bytes", nullable = false)  private long memAvailableBytes;
    @Column(name = "mem_used_bytes", nullable = false)       private long memUsedBytes;
    @Column(name = "mem_used_pct")                           private Double memUsedPct;
    @Column(name = "swap_total_bytes", nullable = false)     private long swapTotalBytes;
    @Column(name = "swap_used_bytes", nullable = false)      private long swapUsedBytes;
    @Column(name = "mem_cached_bytes")                       private Long memCachedBytes;
    @Column(name = "mem_buffers_bytes")                      private Long memBuffersBytes;

    // --- File descriptors ---
    @Column(name = "fd_open")      private Long fdOpen;
    @Column(name = "fd_max")       private Long fdMax;
    @Column(name = "fd_used_pct")  private Double fdUsedPct;

    // --- Host ---
    @Column(name = "host_name", length = 128)    private String hostName;
    @Column(name = "os_name", length = 64)       private String osName;
    @Column(name = "os_version", length = 128)   private String osVersion;
    @Column(name = "os_arch", length = 32)       private String osArch;
    @Column(name = "host_uptime_seconds")        private Long hostUptimeSeconds;
    @Column(name = "host_boot_time")             private Instant hostBootTime;

    // --- Process (SE-Console JVM) ---
    @Column(name = "process_pid")                         private Integer processPid;
    @Column(name = "process_name", length = 128)          private String processName;
    @Column(name = "process_user", length = 128)          private String processUser;
    @Column(name = "process_working_dir", length = 512)   private String processWorkingDir;
    @Column(name = "process_start_time")                  private Instant processStartTime;
    @Column(name = "process_uptime_seconds")              private Long processUptimeSeconds;
    @Column(name = "process_rss_bytes")                   private Long processRssBytes;
    @Column(name = "process_vmem_bytes")                  private Long processVmemBytes;
    @Column(name = "process_thread_count")                private Integer processThreadCount;
    @Column(name = "process_open_files")                  private Long processOpenFiles;

    @Lob
    @Column(name = "disks_json")
    private String disksJson;

    public ConsoleSystemSnapshotEntity() {}

    // --- getters/setters ---------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

    public Double getCpuSystemPct() { return cpuSystemPct; }
    public void setCpuSystemPct(Double cpuSystemPct) { this.cpuSystemPct = cpuSystemPct; }
    public Double getCpuProcessPct() { return cpuProcessPct; }
    public void setCpuProcessPct(Double cpuProcessPct) { this.cpuProcessPct = cpuProcessPct; }
    public Double getLoadAvg1m() { return loadAvg1m; }
    public void setLoadAvg1m(Double loadAvg1m) { this.loadAvg1m = loadAvg1m; }
    public Double getLoadAvg5m() { return loadAvg5m; }
    public void setLoadAvg5m(Double loadAvg5m) { this.loadAvg5m = loadAvg5m; }
    public Double getLoadAvg15m() { return loadAvg15m; }
    public void setLoadAvg15m(Double loadAvg15m) { this.loadAvg15m = loadAvg15m; }
    public Integer getCpuLogicalCores() { return cpuLogicalCores; }
    public void setCpuLogicalCores(Integer cpuLogicalCores) { this.cpuLogicalCores = cpuLogicalCores; }
    public Integer getCpuPhysicalCores() { return cpuPhysicalCores; }
    public void setCpuPhysicalCores(Integer cpuPhysicalCores) { this.cpuPhysicalCores = cpuPhysicalCores; }
    public String getCpuModel() { return cpuModel; }
    public void setCpuModel(String cpuModel) { this.cpuModel = cpuModel; }

    public long getMemTotalBytes() { return memTotalBytes; }
    public void setMemTotalBytes(long memTotalBytes) { this.memTotalBytes = memTotalBytes; }
    public long getMemAvailableBytes() { return memAvailableBytes; }
    public void setMemAvailableBytes(long memAvailableBytes) { this.memAvailableBytes = memAvailableBytes; }
    public long getMemUsedBytes() { return memUsedBytes; }
    public void setMemUsedBytes(long memUsedBytes) { this.memUsedBytes = memUsedBytes; }
    public Double getMemUsedPct() { return memUsedPct; }
    public void setMemUsedPct(Double memUsedPct) { this.memUsedPct = memUsedPct; }
    public long getSwapTotalBytes() { return swapTotalBytes; }
    public void setSwapTotalBytes(long swapTotalBytes) { this.swapTotalBytes = swapTotalBytes; }
    public long getSwapUsedBytes() { return swapUsedBytes; }
    public void setSwapUsedBytes(long swapUsedBytes) { this.swapUsedBytes = swapUsedBytes; }
    public Long getMemCachedBytes() { return memCachedBytes; }
    public void setMemCachedBytes(Long memCachedBytes) { this.memCachedBytes = memCachedBytes; }
    public Long getMemBuffersBytes() { return memBuffersBytes; }
    public void setMemBuffersBytes(Long memBuffersBytes) { this.memBuffersBytes = memBuffersBytes; }

    public Long getFdOpen() { return fdOpen; }
    public void setFdOpen(Long fdOpen) { this.fdOpen = fdOpen; }
    public Long getFdMax() { return fdMax; }
    public void setFdMax(Long fdMax) { this.fdMax = fdMax; }
    public Double getFdUsedPct() { return fdUsedPct; }
    public void setFdUsedPct(Double fdUsedPct) { this.fdUsedPct = fdUsedPct; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public String getOsArch() { return osArch; }
    public void setOsArch(String osArch) { this.osArch = osArch; }
    public Long getHostUptimeSeconds() { return hostUptimeSeconds; }
    public void setHostUptimeSeconds(Long hostUptimeSeconds) { this.hostUptimeSeconds = hostUptimeSeconds; }
    public Instant getHostBootTime() { return hostBootTime; }
    public void setHostBootTime(Instant hostBootTime) { this.hostBootTime = hostBootTime; }

    public Integer getProcessPid() { return processPid; }
    public void setProcessPid(Integer processPid) { this.processPid = processPid; }
    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getProcessUser() { return processUser; }
    public void setProcessUser(String processUser) { this.processUser = processUser; }
    public String getProcessWorkingDir() { return processWorkingDir; }
    public void setProcessWorkingDir(String processWorkingDir) { this.processWorkingDir = processWorkingDir; }
    public Instant getProcessStartTime() { return processStartTime; }
    public void setProcessStartTime(Instant processStartTime) { this.processStartTime = processStartTime; }
    public Long getProcessUptimeSeconds() { return processUptimeSeconds; }
    public void setProcessUptimeSeconds(Long processUptimeSeconds) { this.processUptimeSeconds = processUptimeSeconds; }
    public Long getProcessRssBytes() { return processRssBytes; }
    public void setProcessRssBytes(Long processRssBytes) { this.processRssBytes = processRssBytes; }
    public Long getProcessVmemBytes() { return processVmemBytes; }
    public void setProcessVmemBytes(Long processVmemBytes) { this.processVmemBytes = processVmemBytes; }
    public Integer getProcessThreadCount() { return processThreadCount; }
    public void setProcessThreadCount(Integer processThreadCount) { this.processThreadCount = processThreadCount; }
    public Long getProcessOpenFiles() { return processOpenFiles; }
    public void setProcessOpenFiles(Long processOpenFiles) { this.processOpenFiles = processOpenFiles; }

    public String getDisksJson() { return disksJson; }
    public void setDisksJson(String disksJson) { this.disksJson = disksJson; }
}
