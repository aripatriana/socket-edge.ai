package id.co.jalin.seconsole.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Active TCP connection list. Separate endpoint from NetworkMetricsDto
 * because enumerating all connections on a busy host can be expensive
 * (can be 10K+ connections) and not all views need this data.
 *
 * Frontend polls this at 5s interval, only when the Active Connections
 * card is mounted.
 */
public record NetworkConnectionsDto(
        Instant timestamp,
        int totalCount,
        int returnedCount,        // may be < totalCount if truncated
        boolean truncated,
        List<TcpConnection> connections
) {

    public record TcpConnection(
            String localAddress,
            int localPort,
            String remoteAddress,
            int remotePort,
            String state,
            String protocol,      // "TCP", "TCP6"
            Integer pid           // owning process, null if unknown
    ) {}
}
