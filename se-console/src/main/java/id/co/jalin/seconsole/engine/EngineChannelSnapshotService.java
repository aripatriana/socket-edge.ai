package id.co.jalin.seconsole.engine;

import com.socket.edge.grpc.MetricsBundle;
import id.co.jalin.seconsole.engine.dto.ChannelSummary;
import id.co.jalin.seconsole.engine.history.EngineChannelHistoryService;
import id.co.jalin.seconsole.engine.model.ChannelCfg;
import id.co.jalin.seconsole.engine.model.ChannelSnapshot;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Channel snapshot cache — updated event-driven via {@link GrpcMetricsSubscriber.MetricsListener}.
 *
 * <p>Data flow:
 * <ol>
 *   <li>GrpcMetricsSubscriber.onNext() fires when se-core pushes a bundle.</li>
 *   <li>onBundle() extracts bundle.channel, maps via GrpcChannelSnapshotMapper,
 *       merges with cached config, and updates the cache immediately.</li>
 *   <li>History write is dispatched async — converts proto to HTTP model
 *       (p99 dropped) so the existing schema is unchanged.</li>
 *   <li>Controllers read currentSnapshot() — zero-cost cache read.</li>
 * </ol>
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineChannelSnapshotService implements GrpcMetricsSubscriber.MetricsListener {

    private static final Logger log = LoggerFactory.getLogger(EngineChannelSnapshotService.class);

    private final GrpcMetricsSubscriber subscriber;
    private final EngineConfigService configService;
    private final EngineChannelHistoryService historyService;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    public EngineChannelSnapshotService(GrpcMetricsSubscriber subscriber,
                                        EngineConfigService configService,
                                        EngineChannelHistoryService historyService) {
        this.subscriber     = subscriber;
        this.configService  = configService;
        this.historyService = historyService;
    }

    @PostConstruct
    public void init() {
        subscriber.addListener(this);
    }

    @Override
    public void onBundle(MetricsBundle bundle) {
        if (!bundle.hasChannel()) return;

        try {
            com.socket.edge.grpc.ChannelSnapshot proto = bundle.getChannel();
            Map<String, ChannelCfg> configByName = configService.currentConfigByName();
            List<ChannelSummary> channels = GrpcChannelSnapshotMapper.toChannelList(proto, configByName);

            ChannelSnapshot raw = GrpcChannelSnapshotMapper.toHttpModel(proto);
            snapshot.set(new Snapshot(channels, raw, true, Instant.now().toEpochMilli(), null));

            CompletableFuture.runAsync(() -> historyService.record(raw));
        } catch (Exception ex) {
            Snapshot prev = snapshot.get();
            snapshot.set(new Snapshot(prev.channels(), prev.raw(),
                    false, Instant.now().toEpochMilli(), ex.getMessage()));
            log.warn("Channel snapshot processing failed: {}", ex.getMessage());
        }
    }

    @Override
    public void onDisconnect(String reason) {
        Snapshot prev = snapshot.get();
        snapshot.set(new Snapshot(prev.channels(), prev.raw(),
                false, Instant.now().toEpochMilli(), reason));
    }

    public Snapshot currentSnapshot() { return snapshot.get(); }

    public ChannelSummary findChannel(String name) {
        if (name == null) return null;
        for (ChannelSummary c : snapshot.get().channels()) {
            if (name.equals(c.name())) return c;
        }
        return null;
    }

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
