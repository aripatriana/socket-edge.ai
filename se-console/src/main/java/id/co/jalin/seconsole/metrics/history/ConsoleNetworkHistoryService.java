package id.co.jalin.seconsole.metrics.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.co.jalin.seconsole.dto.response.NetworkMetricsDto;
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
 * Persists network-metric snapshots to {@code console_network_snapshot} (V4).
 * Mirrors {@link ConsoleSystemHistoryService} — same lifecycle, same prune.
 */
@Service
public class ConsoleNetworkHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleNetworkHistoryService.class);

    private final ConsoleNetworkSnapshotRepository repository;
    private final ObjectMapper mapper;
    private final int retentionDays;

    public ConsoleNetworkHistoryService(
            ConsoleNetworkSnapshotRepository repository,
            ObjectMapper mapper,
            @Value("${seconsole.monitoring.network.history.retention-days:7}") int retentionDays) {
        this.repository = repository;
        this.mapper = mapper;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional
    public void record(NetworkMetricsDto dto) {
        if (dto == null) return;
        try {
            ConsoleNetworkSnapshotEntity e = toEntity(dto);
            repository.save(e);
        } catch (Exception ex) {
            log.warn("Failed to persist network snapshot: {}", ex.getMessage());
        }
    }

    /** Hourly prune at :21 past — staggered vs engine (:17, :23) and system (:19). */
    @Scheduled(cron = "0 21 * * * *")
    @Transactional
    public void prune() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        try {
            int deleted = repository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Pruned {} console_network_snapshot rows older than {}", deleted, cutoff);
            }
        } catch (Exception ex) {
            log.warn("Network snapshot prune failed: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ConsoleNetworkSnapshotEntity> between(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)) return Collections.emptyList();
        return repository.findByCapturedAtBetweenOrderByCapturedAtAsc(from, to);
    }

    // -------------------------------------------------------------------------

    private ConsoleNetworkSnapshotEntity toEntity(NetworkMetricsDto dto) {
        ConsoleNetworkSnapshotEntity e = new ConsoleNetworkSnapshotEntity();
        e.setCapturedAt(dto.timestamp() != null ? dto.timestamp() : Instant.now());

        NetworkMetricsDto.TcpStateCounts s = dto.tcpStates();
        if (s != null) {
            e.setTcpTotal(s.total());
            e.setTcpEstablished(s.established());
            e.setTcpTimeWait(s.timeWait());
            e.setTcpCloseWait(s.closeWait());
            e.setTcpListen(s.listen());
            e.setTcpSynSent(s.synSent());
            e.setTcpSynRecv(s.synRecv());
            e.setTcpFinWait1(s.finWait1());
            e.setTcpFinWait2(s.finWait2());
            e.setTcpLastAck(s.lastAck());
            e.setTcpClosing(s.closing());
            e.setTcpOther(s.other());
            e.setTcpStatesRawJson(writeJson(s.rawByState()));
        }

        NetworkMetricsDto.TcpQualityCounters q = dto.tcpQuality();
        if (q != null) {
            e.setTcpRetransSegs(q.retransSegs());
            e.setTcpOutSegs(q.outSegs());
            e.setTcpOutResets(q.outResets());
            e.setTcpInErrs(q.inErrs());
            e.setTcpAttemptFails(q.attemptFails());
            e.setTcpEstabResets(q.estabResets());
            e.setTcpCurrEstab(q.currEstab());
            e.setTcpSyncookiesSent(q.syncookiesSent());
            e.setTcpListenDrops(q.listenDrops());
            e.setTcpListenOverflows(q.listenOverflows());
        }

        e.setInterfacesJson(writeJson(dto.interfaces()));
        e.setListeningPortsJson(writeJson(dto.listeningPorts()));
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
}
