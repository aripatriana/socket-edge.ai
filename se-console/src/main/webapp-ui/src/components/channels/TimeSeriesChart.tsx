import { useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import type {
  EndpointRef,
  HistorySample,
  AggregationKey,
} from '../../api/channels.types';
import { formatLatency } from '../../lib/formatEngine';

/**
 * Unified time-series chart. One component rendering Throughput, Pressure,
 * or Latency based on the {@code metric} prop.
 *
 * <p>Chat 3c-1b r2:
 * <ul>
 *   <li>Endpoint filter + aggregation filter both multi-select, and OWNED
 *       by the parent ({@link #MetricsTab}) so each chart's state is
 *       independent.</li>
 *   <li>All five aggregations (avg/min/max/p90/p95) apply to all three
 *       metrics — the engine exposes them for throughput and pressure too.</li>
 *   <li>Rendered series count = selectedEndpoints × selectedAggregations.
 *       Each series is a separate {@code <Line>} with a unique series key
 *       {@code `${bindingId}:${agg}`}, stable color from endpoint index,
 *       and dasharray from aggregation slot (so avg / min / max lines
 *       of the same endpoint are visually distinguishable).</li>
 * </ul>
 */

export type MetricKey = 'throughputTps' | 'pressureTps' | 'latency';

interface Props {
  title: string;
  subtitle: string;
  metric: MetricKey;
  endpoints: EndpointRef[];
  samplesByHash: Record<string, HistorySample[]>;
  selectedHashes: Set<string>;
  onToggleEndpoint: (bindingId: string) => void;
  selectedAggs: Set<AggregationKey>;
  onToggleAgg: (a: AggregationKey) => void;
}

const LINE_COLORS = [
  '#10b981', // emerald 500
  '#f97316', // orange 500
  '#8b5cf6', // violet 500
  '#0ea5e9', // sky 500
  '#f59e0b', // amber 500
  '#64748b', // slate 500
];

const AGG_OPTIONS: AggregationKey[] = ['avg', 'min', 'max', 'p90', 'p95'];

/**
 * Dash patterns per aggregation. Keep `avg` solid (primary reading) and
 * dash the rest so a chart with e.g. avg + p95 reads as "solid line is the
 * central estimate, dashed is the tail".
 */
const DASH_BY_AGG: Record<AggregationKey, string | undefined> = {
  avg: undefined,       // solid
  min: '2 3',           // dense short-dash
  max: '6 3',           // medium dash
  p90: '4 2',           // dash
  p95: '8 3 2 3',       // dash-dot
};

export function TimeSeriesChart({
  title,
  subtitle,
  metric,
  endpoints,
  samplesByHash,
  selectedHashes,
  onToggleEndpoint,
  selectedAggs,
  onToggleAgg,
}: Props) {
  // Build one row per timestamp, with one column per (bindingId, agg) series.
  const { chartData, seriesKeys } = useMemo(
    () => buildChartData(endpoints, samplesByHash, metric, selectedHashes, selectedAggs),
    [endpoints, samplesByHash, metric, selectedHashes, selectedAggs]
  );

  // (visibleEndpoints helper removed — series are now driven entirely by seriesKeys)

  return (
    <section className="rounded-md border border-border bg-card p-4">
      {/* Header */}
      <header className="mb-3">
        <h3 className="text-[13px] font-semibold text-foreground">{title}</h3>
        <p className="text-[11px] text-muted-foreground mt-0.5">{subtitle}</p>
      </header>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mb-3 pb-3 border-b border-border">
        <FilterGroup label="ENDPOINTS">
          {endpoints.map((e, i) => (
            <EndpointCheckbox
              key={e.bindingId}
              label={e.label}
              colorIndex={i}
              checked={selectedHashes.has(e.bindingId)}
              onChange={() => onToggleEndpoint(e.bindingId)}
              dimmed={e.status === 'DOWN' || e.status === 'ERROR'}
            />
          ))}
        </FilterGroup>

        <FilterGroup label="AGGREGATION">
          {AGG_OPTIONS.map((a) => (
            <AggCheckbox
              key={a}
              label={a}
              dashArray={DASH_BY_AGG[a]}
              checked={selectedAggs.has(a)}
              onChange={() => onToggleAgg(a)}
            />
          ))}
        </FilterGroup>
      </div>

      {/* Chart */}
      <div style={{ width: '100%', height: 240 }}>
        {chartData.length === 0 ? (
          <EmptyChart />
        ) : (
          <ResponsiveContainer>
            <LineChart data={chartData} margin={{ top: 10, right: 16, left: 4, bottom: 4 }}>
              <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="ts"
                tickFormatter={formatClockTickShort}
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                stroke="hsl(var(--border))"
                minTickGap={40}
              />
              <YAxis
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                stroke="hsl(var(--border))"
                tickFormatter={metric === 'latency' ? formatLatencyTick : formatPlainTick}
                width={56}
              />
              <Tooltip
                content={
                  <CustomTooltip
                    metric={metric}
                    seriesKeys={seriesKeys}
                  />
                }
                cursor={{ stroke: 'hsl(var(--border))' }}
              />
              <Legend
                wrapperStyle={{ fontSize: 11 }}
                formatter={(value: string) => {
                  const meta = seriesKeys.get(value);
                  if (!meta) return value;
                  return `${meta.endpoint.label} · ${meta.agg}`;
                }}
              />
              {Array.from(seriesKeys.values()).map((meta) => {
                const color = LINE_COLORS[endpoints.indexOf(meta.endpoint) % LINE_COLORS.length];
                return (
                  <Line
                    key={meta.key}
                    type="monotone"
                    dataKey={meta.key}
                    name={meta.key}
                    stroke={color}
                    strokeWidth={meta.agg === 'avg' ? 1.8 : 1.2}
                    strokeDasharray={DASH_BY_AGG[meta.agg]}
                    dot={false}
                    isAnimationActive={false}
                    connectNulls
                  />
                );
              })}
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  );
}

// ===========================================================================
// Filter sub-components
// ===========================================================================

function FilterGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <span className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">
        {label}
      </span>
      <div className="flex items-center gap-2 flex-wrap">{children}</div>
    </div>
  );
}

function EndpointCheckbox({
  label,
  colorIndex,
  checked,
  onChange,
  dimmed,
}: {
  label: string;
  colorIndex: number;
  checked: boolean;
  onChange: () => void;
  dimmed?: boolean;
}) {
  const color = LINE_COLORS[colorIndex % LINE_COLORS.length];
  return (
    <label className={`inline-flex items-center gap-1.5 text-[11px] cursor-pointer ${dimmed ? 'opacity-60' : ''}`}>
      <input
        type="checkbox"
        checked={checked}
        onChange={onChange}
        className="w-3 h-3 accent-primary"
      />
      <span className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: color }} aria-hidden />
      <span className="font-mono text-foreground">{label}</span>
      {dimmed && <span className="text-muted-foreground">(down)</span>}
    </label>
  );
}

/** Aggregation checkbox with a tiny stroke-preview showing its dash pattern. */
function AggCheckbox({
  label,
  dashArray,
  checked,
  onChange,
}: {
  label: string;
  dashArray?: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <label className="inline-flex items-center gap-1.5 text-[11px] cursor-pointer">
      <input
        type="checkbox"
        checked={checked}
        onChange={onChange}
        className="w-3 h-3 accent-primary"
      />
      {/* Mini line swatch so user sees min vs max vs p95 style */}
      <svg width="20" height="6" aria-hidden>
        <line
          x1="0" y1="3" x2="20" y2="3"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeDasharray={dashArray}
          className="text-muted-foreground"
        />
      </svg>
      <span className={`font-mono ${checked ? 'text-foreground' : 'text-muted-foreground'}`}>
        {label}
      </span>
    </label>
  );
}

function EmptyChart() {
  return (
    <div className="h-full grid place-items-center text-[12px] text-muted-foreground">
      Collecting samples… chart populates within a few seconds.
    </div>
  );
}

// ===========================================================================
// Tooltip
// ===========================================================================

interface SeriesMeta {
  key: string;                  // "{bindingId}:{agg}"
  endpoint: EndpointRef;
  agg: AggregationKey;
}

function CustomTooltip({
  active,
  payload,
  label,
  metric,
  seriesKeys,
}: {
  active?: boolean;
  payload?: Array<{ name: string; value: number; color: string }>;
  label?: number;
  metric: MetricKey;
  seriesKeys: Map<string, SeriesMeta>;
}) {
  if (!active || !payload || payload.length === 0) return null;

  // Group by endpoint so tooltip shows all agg variants together per endpoint.
  const byEndpoint = new Map<string, { label: string; rows: typeof payload }>();
  for (const p of payload) {
    const meta = seriesKeys.get(p.name);
    if (!meta) continue;
    const g = byEndpoint.get(meta.endpoint.bindingId);
    if (g) g.rows.push(p);
    else byEndpoint.set(meta.endpoint.bindingId, { label: meta.endpoint.label, rows: [p] });
  }

  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 shadow-sm text-[11px] max-w-[280px]">
      <div className="font-mono text-muted-foreground mb-1">
        {typeof label === 'number' ? formatClockTickFull(label) : ''}
      </div>
      {Array.from(byEndpoint.values()).map((group) => (
        <div key={group.label} className="mb-1 last:mb-0">
          <div className="font-mono text-foreground text-[10px] uppercase tracking-wide">
            {group.label}
          </div>
          {group.rows.map((p) => {
            const meta = seriesKeys.get(p.name);
            const val = metric === 'latency' ? formatLatency(p.value) : `${p.value} TPS`;
            return (
              <div key={p.name} className="flex items-center gap-2 font-mono pl-2">
                <span className="w-2 h-2 rounded-sm" style={{ backgroundColor: p.color }} />
                <span className="text-muted-foreground">{meta?.agg ?? ''}</span>
                <span className="ml-auto text-foreground">{val}</span>
              </div>
            );
          })}
        </div>
      ))}
    </div>
  );
}

// ===========================================================================
// Data transform — builds one row per timestamp with all visible series as
// columns. Returns an ordered Map of seriesKey → metadata so the chart
// component can reuse it for legend, lines, and tooltip lookup.
// ===========================================================================

type ChartRow = Record<string, number> & { ts: number };

function buildChartData(
  endpoints: EndpointRef[],
  samplesByHash: Record<string, HistorySample[]>,
  metric: MetricKey,
  selectedHashes: Set<string>,
  selectedAggs: Set<AggregationKey>,
): { chartData: ChartRow[]; seriesKeys: Map<string, SeriesMeta> } {

  // Stable order: iterate endpoints in given order, inner iterate agg list order.
  const seriesKeys = new Map<string, SeriesMeta>();
  for (const e of endpoints) {
    if (!selectedHashes.has(e.bindingId)) continue;
    for (const agg of AGG_OPTIONS) {
      if (!selectedAggs.has(agg)) continue;
      const key = `${e.bindingId}:${agg}`;
      seriesKeys.set(key, { key, endpoint: e, agg });
    }
  }

  // Collect timestamps from any visible endpoint. HistorySample.t is epoch ms.
  const allTs = new Set<number>();
  for (const e of endpoints) {
    if (!selectedHashes.has(e.bindingId)) continue;
    for (const s of samplesByHash[e.bindingId] ?? []) allTs.add(s.t);
  }
  const sortedTs = Array.from(allTs).sort((a, b) => a - b);

  // Build per-endpoint timestamp lookup.
  const sampleLookup = new Map<string, Map<number, HistorySample>>();
  for (const e of endpoints) {
    const m = new Map<number, HistorySample>();
    for (const s of samplesByHash[e.bindingId] ?? []) m.set(s.t, s);
    sampleLookup.set(e.bindingId, m);
  }

  const chartData = sortedTs.map((ts) => {
    const row = { ts } as ChartRow;
    for (const { key, endpoint, agg } of seriesKeys.values()) {
      const pt = sampleLookup.get(endpoint.bindingId)?.get(ts);
      if (!pt) continue;
      row[key] = pick(pt, metric, agg);
    }
    return row;
  });

  // Decimate for large windows — recharts slows noticeably past ~1000 points
  // and the visual difference between 40k points and 800 points in a 680px-wide
  // chart is imperceptible. Take every kth row so the first and last rows are
  // always kept (keeps the time axis endpoints stable).
  const decimated = decimate(chartData, 800);

  return { chartData: decimated, seriesKeys };
}

function decimate<T>(arr: T[], maxPoints: number): T[] {
  if (arr.length <= maxPoints) return arr;
  const stride = Math.ceil(arr.length / maxPoints);
  const out: T[] = [];
  for (let i = 0; i < arr.length; i += stride) out.push(arr[i]);
  // Ensure the very last row is present — the loop misses it unless
  // (length - 1) lands on a stride boundary.
  if (out[out.length - 1] !== arr[arr.length - 1]) out.push(arr[arr.length - 1]);
  return out;
}

/**
 * Extract one scalar from a HistorySample for a given (metric, aggregation)
 * pair. Nested access since the rewrite — the 5-tuple distribution now lives
 * inside {@code sample.throughputTps}, {@code sample.pressureTps},
 * {@code sample.latency}.
 */
function pick(p: HistorySample, metric: MetricKey, agg: AggregationKey): number {
  if (metric === 'throughputTps') {
    switch (agg) {
      case 'avg': return p.throughputTps.avg;
      case 'min': return p.throughputTps.min;
      case 'max': return p.throughputTps.max;
      case 'p90': return p.throughputTps.p90;
      case 'p95': return p.throughputTps.p95;
    }
  }
  if (metric === 'pressureTps') {
    switch (agg) {
      case 'avg': return p.pressureTps.avg;
      case 'min': return p.pressureTps.min;
      case 'max': return p.pressureTps.max;
      case 'p90': return p.pressureTps.p90;
      case 'p95': return p.pressureTps.p95;
    }
  }
  // latency
  switch (agg) {
    case 'avg': return p.latency.avgNs;
    case 'min': return p.latency.minNs;
    case 'max': return p.latency.maxNs;
    case 'p90': return p.latency.p90Ns;
    case 'p95': return p.latency.p95Ns;
  }
  // Unreachable — AggregationKey is exhaustive above — but TS needs it.
  return 0;
}

// ===========================================================================
// Axis / tooltip formatters
// ===========================================================================

function formatClockTickShort(ts: number): string {
  const d = new Date(ts);
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  return `${h}:${m}`;
}

function formatClockTickFull(ts: number): string {
  const d = new Date(ts);
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  const s = String(d.getSeconds()).padStart(2, '0');
  return `${h}:${m}:${s}`;
}

function formatPlainTick(n: number): string {
  return `${n}`;
}

function formatLatencyTick(ns: number): string {
  if (!ns || ns <= 0) return '0';
  if (ns < 1_000)        return `${ns}`;
  if (ns < 1_000_000)    return `${(ns / 1_000).toFixed(0)}µs`;
  if (ns < 1_000_000_000) return `${(ns / 1_000_000).toFixed(0)}ms`;
  return `${(ns / 1_000_000_000).toFixed(1)}s`;
}
