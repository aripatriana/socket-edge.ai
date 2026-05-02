package id.co.jalin.seconsole.engine;

import id.co.jalin.seconsole.engine.model.ChannelCfg;
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
 * Polls {@code GET /config/channels} on a slow cadence (default 30s) and
 * caches the result as a {@code name → ChannelCfg} map for fast lookup
 * during the metrics merge.
 *
 * <p>{@code initialDelay=0} seeds the cache as soon as possible after boot,
 * so {@link EngineChannelSnapshotService}'s first poll already has config context
 * (listen port, client strategy).
 *
 * <p>If a poll fails, the previously-cached map is retained.
 */
@Service
@ConditionalOnProperty(prefix = "seconsole.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EngineConfigService {

    private static final Logger log = LoggerFactory.getLogger(EngineConfigService.class);

    private final EngineClient client;
    private final AtomicReference<Map<String, ChannelCfg>> cache = new AtomicReference<>(Collections.emptyMap());

    public EngineConfigService(EngineClient client) {
        this.client = client;
    }

    @Scheduled(
            initialDelay = 0,
            fixedDelayString = "${seconsole.engine.config.poll-interval-ms:30000}"
    )
    public void poll() {
        try {
            List<ChannelCfg> cfgs = client.getChannels();
            Map<String, ChannelCfg> byName = new HashMap<>(cfgs.size() * 2);
            for (ChannelCfg c : cfgs) {
                if (c != null && c.name() != null) byName.put(c.name(), c);
            }
            cache.set(Collections.unmodifiableMap(byName));
        } catch (Exception ex) {
            log.warn("Engine /config/channels poll failed, keeping previous cache: {}", ex.getMessage());
        }
    }

    /** Snapshot of {@code name → ChannelCfg}. Never null; empty before first successful poll. */
    public Map<String, ChannelCfg> currentConfigByName() {
        return cache.get();
    }
}
