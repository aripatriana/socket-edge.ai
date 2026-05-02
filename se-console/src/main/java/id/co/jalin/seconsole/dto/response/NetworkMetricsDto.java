package id.co.jalin.seconsole.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * OS-level network metrics for Monitoring → Network tab.
 *
 * Scope: all fields sourced from OSHI + /proc/net/*. Engine-specific
 * enrichment (channel binding per port, Netty Accepts/sec, per-channel
 * traffic rate) will be added via separate EngineNetworkMetricsDto in
 * Chat 3c. This DTO stays independent of engine JMX availability.
 */
public record NetworkMetricsDto(
        Instant timestamp,
        List<NetworkInterfaceInfo> interfaces,
        TcpStateCounts tcpStates,
        TcpQualityCounters tcpQuality,
        List<ListeningPort> listeningPorts
) {

    /**
     * Per-interface snapshot. Cumulative bytes/packets since interface
     * came up — deltas are computed frontend-side from two consecutive
     * snapshots (same pattern as Chat 3b+ GcRateChart).
     */
    public record NetworkInterfaceInfo(
            String name,
            String displayName,
            String macAddress,
            List<String> ipv4Addresses,
            List<String> ipv6Addresses,
            long speedBitsPerSecond,   // -1 if unknown
            long mtu,
            boolean up,
            long bytesRecv,            // cumulative since boot/up
            long bytesSent,
            long packetsRecv,
            long packetsSent,
            long inErrors,
            long outErrors,
            long inDrops,
            long outDrops
    ) {}

    /**
     * Count of TCP connections by state. Sum across all local interfaces.
     *
     * Only states that commonly appear in production are broken out;
     * remaining states are rolled up into `other`.
     */
    public record TcpStateCounts(
            int total,
            int established,
            int timeWait,
            int closeWait,
            int listen,
            int synSent,
            int synRecv,
            int finWait1,
            int finWait2,
            int lastAck,
            int closing,
            int other,
            Map<String, Integer> rawByState  // full breakdown, keyed by state name
    ) {}

    /**
     * TCP stack counters from /proc/net/snmp + /proc/net/netstat.
     * All cumulative since system boot. Rates are computed frontend-side.
     *
     * Fields nullable — set to null if the counter couldn't be parsed
     * (non-Linux or unusual kernel config).
     */
    public record TcpQualityCounters(
            Long retransSegs,         // Tcp.RetransSegs
            Long outSegs,             // Tcp.OutSegs — used to compute retrans %
            Long outResets,           // Tcp.OutRsts
            Long inErrs,              // Tcp.InErrs
            Long attemptFails,        // Tcp.AttemptFails
            Long estabResets,         // Tcp.EstabResets
            Long currEstab,           // Tcp.CurrEstab
            Long syncookiesSent,      // TcpExt.SyncookiesSent
            Long listenDrops,         // TcpExt.ListenDrops
            Long listenOverflows      // TcpExt.ListenOverflows
    ) {}

    /**
     * A port the JVM has bound and is accepting connections on.
     * `establishedCount` is derived from OS connection table —
     * Chat 3c will add `channelId`, `acceptsPerSecond`, `rateIn`, `rateOut`
     * fields once engine JMX is wired.
     */
    public record ListeningPort(
            String localAddress,      // "0.0.0.0", "::", "127.0.0.1"
            int port,
            String protocol,          // "TCP", "TCP6"
            int establishedCount,     // sockets in ESTABLISHED state for this local port
            Integer pid               // owning process ID, null if unknown
    ) {}
}
