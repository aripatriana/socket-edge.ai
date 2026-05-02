import type { SystemSnapshotRow } from '../api/metrics.types';
import type { EngineJvmSnapshotRow, JvmSnapshotRow } from '../api/jvm.types';
import type { NetworkSnapshotRow } from '../api/network.types';

/**
 * Adapters that turn `*SnapshotRow[]` (historical DB rows) into the
 * {t, value, ...} shapes chart components already expect.
 *
 * The live charts use `useMetricHistory` which returns `{t, v}` where `t`
 * is "ms since page mount". For historical mode we produce `{t, v}` where
 * `t` is the absolute capture epoch — recharts XAxis is hidden by default
 * on these cards so the shape is all that matters; if an axis is shown
 * later, `tickFormatter` can format from the epoch.
 */

export interface ChartPoint {
  t: number;
  v: number;
}

export interface DualChartPoint {
  t: number;
  primary: number | null;
  secondary: number | null;
}

/**
 * JVM adapters accept either the console's own snapshot row or the engine's.
 * The columns they read (capturedAt, heapUsed, heapMax, gcTotalCount) exist
 * in both shapes, so the same chart component can render either source.
 */
export type AnyJvmRow = JvmSnapshotRow | EngineJvmSnapshotRow;

// -----------------------------------------------------------------------------
// SYSTEM adapters
// -----------------------------------------------------------------------------

/**
 * CPU chart needs two series: system + process. Rows where both are null
 * are skipped (platform couldn't probe). Null-on-one-side is preserved so
 * recharts renders a gap rather than connecting across missing points.
 */
export function cpuSeriesFromRows(rows: SystemSnapshotRow[]): DualChartPoint[] {
  return rows.map((r) => ({
    t: Date.parse(r.capturedAt),
    primary: r.cpuProcessPct,
    secondary: r.cpuSystemPct,
  }));
}

// -----------------------------------------------------------------------------
// JVM adapters
// -----------------------------------------------------------------------------

/**
 * Heap % over time. Computed from used/max ratio — max can be -1 when
 * unbounded, in which case we skip (recharts handles undefined as gap).
 */
export function heapPctSeriesFromRows(rows: AnyJvmRow[]): ChartPoint[] {
  return rows
    .map((r) => {
      if (r.heapMax <= 0) return null;
      const pct = (r.heapUsed / r.heapMax) * 100;
      return { t: Date.parse(r.capturedAt), v: pct };
    })
    .filter((p): p is ChartPoint => p !== null);
}

/**
 * Per-collector GC rate (events per minute). Rows carry cumulative totals;
 * we compute the delta between consecutive rows and divide by the elapsed
 * seconds. If the elapsed is zero (dedupe) or totals go backwards (JVM
 * restart mid-window), the row is dropped.
 *
 * Returns a single aggregate "gc" series. Per-collector split requires the
 * per-collector JSON blob; we defer that to a follow-up since the live
 * GcRateChart also aggregates visually in most cases.
 */
export interface GcRatePoint {
  t: number;
  gc: number;   // events/min
}

export function gcRateSeriesFromRows(rows: AnyJvmRow[]): GcRatePoint[] {
  const out: GcRatePoint[] = [];
  for (let i = 1; i < rows.length; i++) {
    const prev = rows[i - 1];
    const curr = rows[i];
    const dCount = curr.gcTotalCount - prev.gcTotalCount;
    const dMs = Date.parse(curr.capturedAt) - Date.parse(prev.capturedAt);
    if (dMs <= 0 || dCount < 0) continue;
    const eventsPerMin = (dCount / dMs) * 60_000;
    out.push({ t: Date.parse(curr.capturedAt), gc: eventsPerMin });
  }
  return out;
}

// -----------------------------------------------------------------------------
// NETWORK adapters
// -----------------------------------------------------------------------------

/**
 * Per-interface RX/TX bytes over time. The raw rows serialize all
 * interfaces into `interfacesJson`; we parse and pick the one by name.
 * Result is cumulative bytes — the chart does its own delta if it wants
 * a rate.
 */
export interface InterfaceRow {
  name: string;
  bytesRecv: number;
  bytesSent: number;
}

export function interfaceSeriesFromRows(
  rows: NetworkSnapshotRow[],
  ifaceName: string
): { t: number; rx: number; tx: number }[] {
  const out: { t: number; rx: number; tx: number }[] = [];
  for (const r of rows) {
    if (!r.interfacesJson) continue;
    try {
      const list = JSON.parse(r.interfacesJson) as InterfaceRow[];
      const match = list.find((i) => i.name === ifaceName);
      if (!match) continue;
      out.push({
        t: Date.parse(r.capturedAt),
        rx: match.bytesRecv,
        tx: match.bytesSent,
      });
    } catch {
      // Malformed row — skip silently; a single bad sample shouldn't break the chart.
    }
  }
  return out;
}

/**
 * TCP established count over time. Flat column so no JSON parsing.
 */
export function tcpEstablishedSeriesFromRows(
  rows: NetworkSnapshotRow[]
): ChartPoint[] {
  return rows.map((r) => ({
    t: Date.parse(r.capturedAt),
    v: r.tcpEstablished,
  }));
}
