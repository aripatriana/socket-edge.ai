package id.co.jalin.seconsole.engine;

import id.co.jalin.seconsole.engine.dto.ChannelSummary;
import id.co.jalin.seconsole.engine.history.EngineChannelHistoryService;
import id.co.jalin.seconsole.engine.model.ChannelCfg;
import id.co.jalin.seconsole.engine.model.ChannelSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Poller for the engine's {@code /socket/snapshot/channels} endpoint —
 * replaces the pre-rewrite {@code EngineMetricsService} that called three
 * separate endpoints ({@code /socket/status, /socket/metrics, /socket/queues}).
 *
 * <p>Per-tick flow (cf. {@code snapshot_seconsole.png}):
 * <ol>
 *   <li>HTTP GET snapshot</li>
 *   <li>Merge with cached channel config → grouped {@link ChannelSummary} list</li>
 *   <li>Store result in the AtomicReference (hot read path for the UI)</li>
 *   <li>Persist raw snapshot to H2 via {@link EngineChannelHistoryService}</li>
 * </ol>
 *
 * <p>Any exception preserves the last-good cache state and sets
 * {@code reachable=false} on the envelope — so the UI can surface "stale"
 * without flashing empty state on a transient engine blip.
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineChannelSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(EngineChannelSnapshotService.class);

    private final EngineClient client;
    private final EngineConfigService configService;
    private final EngineChannelHistoryService historyService;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    public EngineChannelSnapshotService(EngineClient client,
                                        EngineConfigService configService,
                                        EngineChannelHistoryService historyService) {
        this.client = client;
        this.configService = configService;
        this.historyService = historyService;
    }

    @Scheduled(fixedDelayString = "${seconsole.engine.channels.poll-interval-ms:2000}")
    public void poll() {
        try {
            ChannelSnapshot raw = client.getChannelSnapshot();
            Map<String, ChannelCfg> configByName = configService.currentConfigByName();
            List<ChannelSummary> merged = ChannelSnapshotMapper.toChannelList(raw, configByName);

            // Cache first — the UI's hot path should see fresh data even
            // if the DB write below fails.
            snapshot.set(new Snapshot(
                    merged, raw,
                    true, Instant.now().toEpochMilli(), null));

            historyService.record(raw);
        } catch (Exception ex) {
            Snapshot prev = snapshot.get();
            snapshot.set(new Snapshot(
                    prev.channels(), prev.raw(),
                    false, Instant.now().toEpochMilli(), ex.getMessage()));
            log.warn("Channel snapshot poll failed: {}", ex.getMessage());
        }
    }

    /** Latest cache entry for controllers. */
    public Snapshot currentSnapshot() { return snapshot.get(); }

    /** Find a single channel by name — O(N) over a small list, fine for N~20. */
    public ChannelSummary findChannel(String name) {
        if (name == null) return null;
        for (ChannelSummary c : snapshot.get().channels()) {
            if (name.equals(c.name())) return c;
        }
        return null;
    }

    /**
     * Cache envelope. Keeps both the grouped {@link ChannelSummary} list
     * (for list/detail views) AND the raw snapshot (for the new
     * {@code /api/channels/snapshot} endpoint, should we surface it).
     */
    public record Snapshot(
            List<ChannelSummary> channels,
            ChannelSnapshot raw,
            boolean reachable,
            long lastUpdateMillis,
            String lastError
    ) {
        public static Snapshot empty() {
            return new Snapshot(Collections.emptyList(), null, false, 0L, null);
        }
    }
}
