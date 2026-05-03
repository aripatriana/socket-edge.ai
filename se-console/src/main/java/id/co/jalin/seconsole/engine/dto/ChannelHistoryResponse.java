package id.co.jalin.seconsole.engine.dto;

import java.util.List;
import java.util.Map;

/**
 * Chart data for one channel. Returned by
 * {@code GET /api/channels/{name}/history?window=4m}.
 *
 * <p>Chat 3e-3 rewrite: samples now come from the H2 parent-child history
 * tables, not the in-memory ring buffer. Each {@link HistorySample} carries
 * the full metric distribution so the frontend can plot any of latency /
 * pressure / throughput (avg/min/max/p90/p95) without re-querying.
 *
 * <p>{@code endpoints} is a stable-ordered list — server first, then clients —
 * so the UI can assign colors consistently across refreshes. {@code status}
 * reflects the <em>current</em> state (from the live cache), not historical.
 *
 * @param channelName    name of the channel
 * @param windowMs       requested window duration in milliseconds (echo)
 * @param now            server clock epoch ms when this response was assembled
 * @param endpoints      display metadata per endpoint (current status)
 * @param samplesByHashId hashId → oldest-first time-series samples
 */
public record ChannelHistoryResponse(
        String channelName,
        long windowMs,
        long now,
        List<EndpointRef> endpoints,
        Map<String, List<HistorySample>> samplesByHashId
) {

    /**
     * Display info for one endpoint. {@code label} is the short form shown
     * in chart legends; {@code fullId} is the engine's verbose id for
     * tooltips; {@code status} is the current runtime state for disabled
     * styling (drawn from the live cache, not historical).
     */
    public record EndpointRef(
            String hashId,
            String label,
            String fullId,
            String type,       // SERVER | CLIENT
            String status      // current runtime state
    ) {}

    /**
     * One historical sample for one socket — a direct projection of an
     * {@code engine_channel_socket_sample} row. Nested structure mirrors
     * {@link SocketSummary.Metrics} so the frontend can reuse the same
     * chart components for live and historical data.
     *
     * @param t            epoch ms timestamp (from {@code captured_at})
     * @param state        socket state at sample time
     * @param queueDepth   queue depth
     * @param latency      latency distribution (ns)
     * @param pressureTps  pressure distribution
     * @param throughputTps throughput distribution
     */
    public record HistorySample(
            long t,
            String state,
            long queueDepth,
            SocketSummary.Stat latency,
            SocketSummary.Stat pressureTps,
            SocketSummary.Stat throughputTps
    ) {}
}
