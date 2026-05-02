package id.co.jalin.seconsole.engine.model;

import java.util.List;

/**
 * Mirrors the engine's {@code ChannelCfg} record returned by {@code GET /config/channels}.
 *
 * <p>One {@code ChannelCfg} == one {@code ChannelGroup} == one server socket bind
 * plus N client endpoints (either side optional — a channel can be server-only
 * or client-only).
 */
public record ChannelCfg(
        String name,
        String type,
        ServerChannel server,
        ClientChannel client,
        List<String> profiles,
        String unknownMti
) {
    public record ServerChannel(
            String listenHost,
            int listenPort,
            List<SocketEndpoint> pool,
            String strategy
    ) {}

    public record ClientChannel(
            List<SocketEndpoint> endpoints,
            String strategy
    ) {}

    public record SocketEndpoint(
            String host,
            int port,
            int weight,
            int priority,
            int maxfails,
            int failTimeout
    ) {}
}
