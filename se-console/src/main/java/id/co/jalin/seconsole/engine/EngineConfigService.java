package id.co.jalin.seconsole.engine;

import com.socket.edge.grpc.ChannelConfig;
import com.socket.edge.grpc.ChannelConfigList;
import com.socket.edge.grpc.ClientChannelConfig;
import com.socket.edge.grpc.ServerChannelConfig;
import com.socket.edge.grpc.SocketEndpointConfig;
import id.co.jalin.seconsole.engine.model.ChannelCfg;
import id.co.jalin.seconsole.grpc.CoreGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches channel configuration from se-core via gRPC {@code GetChannelConfigs}
 * on a slow cadence (default 30s) and caches the result as a
 * {@code name → ChannelCfg} map for fast lookup.
 *
 * <p>Uses the existing {@link CoreGrpcClient} — no additional protocol needed.
 * Se-console calls this once on connect and after each config reload.
 *
 * <p>If a poll fails, the previously-cached map is retained.
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineConfigService {

    private static final Logger log = LoggerFactory.getLogger(EngineConfigService.class);

    private final CoreGrpcClient grpcClient;
    private final AtomicReference<Map<String, ChannelCfg>> cache = new AtomicReference<>(Collections.emptyMap());

    public EngineConfigService(CoreGrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    @Scheduled(
            initialDelay = 0,
            fixedDelayString = "${seconsole.engine.config.poll-interval-ms:30000}"
    )
    public void poll() {
        try {
            ChannelConfigList list = grpcClient.getChannelConfigs();
            Map<String, ChannelCfg> byName = new HashMap<>(list.getChannelsCount() * 2);
            for (ChannelConfig c : list.getChannelsList()) {
                ChannelCfg cfg = toModel(c);
                if (cfg.name() != null && !cfg.name().isBlank()) {
                    byName.put(cfg.name(), cfg);
                }
            }
            cache.set(Collections.unmodifiableMap(byName));
        } catch (Exception ex) {
            log.warn("gRPC GetChannelConfigs failed, keeping previous cache: {}", ex.getMessage());
        }
    }

    /** Snapshot of {@code name → ChannelCfg}. Never null; empty before first successful poll. */
    public Map<String, ChannelCfg> currentConfigByName() {
        return cache.get();
    }

    // ── Proto → model mapping ─────────────────────────────────────────────────

    private static ChannelCfg toModel(ChannelConfig c) {
        return new ChannelCfg(
                blankToNull(c.getName()),
                blankToNull(c.getType()),
                c.hasServer() ? toServerModel(c.getServer()) : null,
                c.hasClient() ? toClientModel(c.getClient()) : null,
                c.getProfilesList().isEmpty() ? null : c.getProfilesList(),
                blankToNull(c.getUnknownMti())
        );
    }

    private static ChannelCfg.ServerChannel toServerModel(ServerChannelConfig s) {
        List<ChannelCfg.SocketEndpoint> pool = s.getPoolList().stream()
                .map(EngineConfigService::toEndpointModel)
                .toList();
        return new ChannelCfg.ServerChannel(
                blankToNull(s.getListenHost()),
                s.getListenPort(),
                pool,
                blankToNull(s.getStrategy())
        );
    }

    private static ChannelCfg.ClientChannel toClientModel(ClientChannelConfig c) {
        List<ChannelCfg.SocketEndpoint> endpoints = c.getEndpointsList().stream()
                .map(EngineConfigService::toEndpointModel)
                .toList();
        return new ChannelCfg.ClientChannel(
                endpoints,
                blankToNull(c.getStrategy())
        );
    }

    private static ChannelCfg.SocketEndpoint toEndpointModel(SocketEndpointConfig ep) {
        return new ChannelCfg.SocketEndpoint(
                blankToNull(ep.getHost()),
                ep.getPort(),
                ep.getWeight(),
                ep.getPriority(),
                ep.getMaxfails(),
                ep.getFailTimeout()
        );
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
