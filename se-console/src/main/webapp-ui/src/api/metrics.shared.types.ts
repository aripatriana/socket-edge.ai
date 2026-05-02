/**
 * Envelope shape returned by /api/console/<domain>/latest endpoints.
 * Carries the metrics DTO plus probe reachability metadata used to render
 * "stale" / "probe failed" states on the dashboard.
 */
export interface MetricsEnvelope<T> {
  metrics: T;
  reachable: boolean;
  lastUpdateMillis: number;
  lastError: string | null;
}

/**
 * Time-range parameter shape for /history endpoints. `from` and `to` are
 * ISO-8601 strings (UTC) — backend parses them via @DateTimeFormat.
 */
export interface HistoryRange {
  from: string;
  to: string;
}
