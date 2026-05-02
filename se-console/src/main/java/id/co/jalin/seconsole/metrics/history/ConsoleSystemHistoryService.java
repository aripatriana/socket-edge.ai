package id.co.jalin.seconsole.metrics.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Persists system-metric snapshots to {@code console_system_snapshot} (V4).
 *
 * <p>Architecture — mirrors {@link
 * id.co.jalin.seconsole.engine.history.EngineJvmHistoryService}: the
 * scheduled poller in {@code ConsoleSystemMetricsService} writes to the
 * in-memory {@code AtomicReference} cache AND calls {@link #record} in the
 * same tick. Controllers read the cache; history queries hit this service.
 *
 * <p>Retention enforced by {@link #prune()}; default 7 days, configurable
 * via {@code seconsole.monitoring.system.history.retention-days}.
 */
@Service
public class ConsoleSystemHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSystemHistoryService.class);

    private final ConsoleSystemSnapshotRepository repository;
    private final ObjectMapper mapper;
    private final int retentionDays;

    public ConsoleSystemHistoryService(
            ConsoleSystemSnapshotRepository repository,
            ObjectMapper mapper,
            @Value("${seconsole.monitoring.system.history.retention-days:7}") int retentionDays) {
        this.repository = repository;
        this.mapper = mapper;
        this.retentionDays = Math.max(1, retentionDays);
    }

    /**
     * Persist one tick. Invoked synchronously after the poller updates the
     * cache. DB failures are logged but swallowed — the live dashboard must
     * not break on a transient DB problem.
     */
    @Transactional
    public void record(SystemMetricsDto dto) {
        if (dto == null) return;
        try {
            ConsoleSystemSnapshotEntity e = toEntity(dto);
            repository.save(e);
        } catch (Exception ex) {
            log.warn("Failed to persist system snapshot: {}", ex.getMessage());
        }
    }

    /** Hourly prune at :19 past — staggered vs engine pruners (:17, :23). */
    @Scheduled(cron = "0 19 * * * *")
    @Transactional
    public void prune() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try {
            int deleted = repository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Pruned {} console_system_snapshot rows older than {}", deleted, cutoff);
            }
        } catch (Exception ex) {
            log.warn("System snapshot prune failed: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ConsoleSystemSnapshotEntity> between(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)) return Collections.emptyList();
        return repository.findByCapturedAtBetweenOrderByCapturedAtAsc(from, to);
    }

    // -------------------------------------------------------------------------

    private ConsoleSystemSnapshotEntity toEntity(SystemMetricsDto dto) {
        ConsoleSystemSnapshotEntity e = new ConsoleSystemSnapshotEntity();

        e.setCapturedAt(dto.timestamp() != null ? dto.timestamp() : Instant.now());

        SystemMetricsDto.CpuMetrics cpu = dto.cpu();
        if (cpu != null) {
            e.setCpuProcessPct(cpu.processCpuPercent());
            e.setCpuSystemPct(cpu.systemCpuPercent());
            e.setLoadAvg1m(cpu.loadAvg1m());
            e.setLoadAvg5m(cpu.loadAvg5m());
            e.setLoadAvg15m(cpu.loadAvg15m());
            e.setCpuLogicalCores(cpu.logicalCores());
            e.setCpuPhysicalCores(cpu.physicalCores());
            e.setCpuModel(trim(cpu.model(), 256));
        }

        SystemMetricsDto.MemoryMetrics m = dto.memory();
        if (m != null) {
            e.setMemTotalBytes(m.totalBytes());
            e.setMemAvailableBytes(m.availableBytes());
            e.setMemUsedBytes(m.usedBytes());
            e.setMemUsedPct(m.usedPercent());
            e.setSwapTotalBytes(m.swapTotalBytes());
            e.setSwapUsedBytes(m.swapUsedBytes());
            e.setMemCachedBytes(m.cachedBytes());
            e.setMemBuffersBytes(m.buffersBytes());
        }

        SystemMetricsDto.FileDescriptorMetrics fd = dto.fileDescriptors();
        if (fd != null) {
            e.setFdOpen(fd.open());
            e.setFdMax(fd.max());
            e.setFdUsedPct(fd.usedPercent());
        }

        SystemMetricsDto.HostInfo h = dto.host();
        if (h != null) {
            e.setHostName(trim(h.hostname(), 128));
            e.setOsName(trim(h.osName(), 64));
            e.setOsVersion(trim(h.osVersion(), 128));
            e.setOsArch(trim(h.osArch(), 32));
            e.setHostUptimeSeconds(h.uptimeSeconds());
            e.setHostBootTime(h.bootTime());
        }

        SystemMetricsDto.ProcessMetrics p = dto.process();
        if (p != null) {
            e.setProcessPid(p.pid());
            e.setProcessName(trim(p.processName(), 128));
            e.setProcessUser(trim(p.user(), 128));
            e.setProcessWorkingDir(trim(p.workingDirectory(), 512));
            e.setProcessStartTime(p.startTime());
            e.setProcessUptimeSeconds(p.uptimeSeconds());
            e.setProcessRssBytes(p.residentSetSizeBytes());
            e.setProcessVmemBytes(p.virtualMemorySizeBytes());
            e.setProcessThreadCount(p.threadCount());
            e.setProcessOpenFiles(p.openFileCount());
        }

        e.setDisksJson(writeJson(dto.disks()));
        return e;
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.debug("JSON serialization failed: {}", ex.getMessage());
            return null;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
