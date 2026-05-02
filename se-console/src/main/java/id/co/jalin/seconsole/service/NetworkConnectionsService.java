package id.co.jalin.seconsole.service;

import id.co.jalin.seconsole.dto.response.NetworkConnectionsDto;
import id.co.jalin.seconsole.dto.response.NetworkConnectionsDto.TcpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.InternetProtocolStats.IPConnection;
import oshi.software.os.OperatingSystem;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dedicated TCP connection enumeration.
 *
 * Kept separate from NetworkMetricsService because:
 *  - Connection list can be large (10K+ on busy hosts)
 *  - Not all views need full list; summary counts are in NetworkMetricsService
 *  - Callers that only need metrics shouldn't pay the enumeration cost
 *
 * Connections are capped to protect serialization cost and UI rendering —
 * if a host has > MAX_CONNECTIONS active, we return the first chunk with
 * a `truncated` flag so the UI can display "showing 1000 of 12,453".
 *
 * Sort order: ESTABLISHED first (most informative), then by local port
 * (stable ordering for operator eye tracking).
 */
@Service
public class NetworkConnectionsService {

    private static final Logger log = LoggerFactory.getLogger(NetworkConnectionsService.class);

    /** Upper bound on connections returned to frontend. */
    private static final int MAX_CONNECTIONS = 1000;

    /** Cache TTL for connection enumeration. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final SystemInfo si = new SystemInfo();
    private final OperatingSystem os = si.getOperatingSystem();

    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    private record Snapshot(Instant taken, NetworkConnectionsDto dto) {}

    public NetworkConnectionsDto snapshot() {
        Snapshot current = cache.get();
        if (current != null && Duration.between(current.taken, Instant.now()).compareTo(CACHE_TTL) < 0) {
            return current.dto;
        }
        NetworkConnectionsDto fresh = probe();
        cache.set(new Snapshot(Instant.now(), fresh));
        return fresh;
    }

    private NetworkConnectionsDto probe() {
        List<IPConnection> raw;
        try {
            InternetProtocolStats ipStats = os.getInternetProtocolStats();
            raw = ipStats.getConnections();
        } catch (Throwable t) {
            log.debug("Could not enumerate TCP connections: {}", t.getMessage());
            raw = Collections.emptyList();
        }

        int total = raw.size();
        boolean truncated = total > MAX_CONNECTIONS;

        // Sort: ESTABLISHED first (most useful), then by local port asc.
        List<IPConnection> sorted = new ArrayList<>(raw);
        sorted.sort(
                Comparator.<IPConnection, Integer>comparing(c -> {
                    var state = c.getState();
                    if (state == null) return 99;
                    return switch (state) {
                        case ESTABLISHED -> 0;
                        case LISTEN -> 1;
                        case TIME_WAIT -> 2;
                        case CLOSE_WAIT -> 3;
                        default -> 4;
                    };
                }).thenComparingInt(IPConnection::getLocalPort)
        );

        List<TcpConnection> out = new ArrayList<>(Math.min(total, MAX_CONNECTIONS));
        for (int i = 0; i < total && i < MAX_CONNECTIONS; i++) {
            IPConnection c = sorted.get(i);
            out.add(new TcpConnection(
                    formatAddress(c.getLocalAddress()),
                    c.getLocalPort(),
                    formatAddress(c.getForeignAddress()),
                    c.getForeignPort(),
                    c.getState() != null ? c.getState().name() : "UNKNOWN",
                    isIpv6(c.getLocalAddress()) ? "TCP6" : "TCP",
                    c.getowningProcessId() > 0 ? c.getowningProcessId() : null
            ));
        }

        return new NetworkConnectionsDto(Instant.now(), total, out.size(), truncated, out);
    }

    private static String formatAddress(byte[] addr) {
        if (addr == null) return "";
        try {
            return java.net.InetAddress.getByAddress(addr).getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isIpv6(byte[] addr) {
        return addr != null && addr.length == 16;
    }
}
