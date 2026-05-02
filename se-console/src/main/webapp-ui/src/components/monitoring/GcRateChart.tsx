import { useEffect, useRef, useState } from 'react';
import { Line, LineChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Card } from '../dashboard/Card';
import type { GcCollector } from '../../api/jvm.types';
import { formatRate } from '../../lib/format';
import { gcRateSeriesFromRows, type AnyJvmRow } from '../../lib/snapshotAdapters';

/**
 * GC Rate chart — events/minute per collector over time, computed from
 * the delta between consecutive cumulative snapshots.
 *
 * The FIRST sample after mount is used as a baseline only — no rate is
 * plotted for it. Otherwise the first delta would show the total GC count
 * since JVM startup compressed into one 2-second window, producing a
 * spurious 60/min spike on page load.
 *
 * Historical mode: when `historyRows` is given, the chart plots a single
 * aggregate "gc events/min" line derived from row-to-row deltas of
 * gc_total_count. Per-collector split is preserved in Live mode only —
 * splitting historical rows would require parsing each row's gcJson and
 * is deferred until a user asks for it.
 */
interface Sample {
  t: number;
  timestamp: number;
  counts: Map<string, number>;
  times: Map<string, number>;
}

interface DisplayPoint {
  t: number;
  [collectorName: string]: number;
}

function shortName(name: string): string {
  return name.replace(/Generation/i, '').replace(/Collector/i, '').trim();
}

const COLORS = [
  'hsl(var(--primary))',
  'hsl(var(--accent))',
  'hsl(var(--muted-foreground))',
  '#b47b00',
];

export function GcRateChart({
  collectors,
  timestamp,
  historyRows,
  windowSec = 60,
  maxPoints = 60,
}: {
  collectors: GcCollector[];
  timestamp: string;
  historyRows?: AnyJvmRow[];
  windowSec?: number;
  maxPoints?: number;
}) {
  const isHistory = !!historyRows;
  const startRef = useRef<number>(Date.now());
  const samplesRef = useRef<Sample[]>([]);
  const lastTsRef = useRef<string | null>(null);
  const [displayPoints, setDisplayPoints] = useState<DisplayPoint[]>([]);

  useEffect(() => {
    // Historical mode — data comes from props, no rolling buffer needed.
    if (isHistory) return;
    if (timestamp === lastTsRef.current) return;
    lastTsRef.current = timestamp;

    const now = Date.now();
    const counts = new Map<string, number>();
    const times = new Map<string, number>();
    for (const c of collectors) {
      counts.set(c.name, c.collectionCount);
      times.set(c.name, c.collectionTimeMs);
    }

    const sample: Sample = {
      t: now - startRef.current,
      timestamp: now,
      counts,
      times,
    };

    const buf = samplesRef.current;
    buf.push(sample);
    if (buf.length > maxPoints + 1) buf.shift();

    // The very first sample establishes a baseline — no chart data yet.
    // Starting from the second sample, we can compute rate since-baseline.
    if (buf.length < 2) return;

    const prev = buf[buf.length - 2];
    const curr = buf[buf.length - 1];
    const dtSec = (curr.timestamp - prev.timestamp) / 1000;
    if (dtSec <= 0) return;

    const point: DisplayPoint = { t: curr.t };
    for (const c of collectors) {
      const dCount = (curr.counts.get(c.name) ?? 0) - (prev.counts.get(c.name) ?? 0);
      point[shortName(c.name)] = Math.max(0, (dCount / dtSec) * 60);
    }
    setDisplayPoints((prevPoints) => {
      const next = [...prevPoints, point];
      if (next.length > maxPoints) next.shift();
      return next;
    });
  }, [timestamp, collectors, maxPoints, isHistory]);

  const ratesSummary = computeRatesSummary(samplesRef.current, windowSec);

  // Historical mode uses aggregate gcTotalCount deltas from the row stream.
  const historicalPoints = isHistory
    ? gcRateSeriesFromRows(historyRows).map((p) => ({
        t: p.t,
        gc: p.gc,
      }))
    : null;
  const chartData = isHistory ? historicalPoints! : displayPoints;
  const hasData = chartData.length >= 2;

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            GC Rate
          </div>
          <div className="text-[11px] text-muted-foreground">
            {isHistory
              ? `events/min · ${historyRows!.length} samples in range`
              : `events/min · last ${windowSec}s window`}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-[1fr_180px] gap-4">
        <div style={{ height: 160 }}>
          {!hasData ? (
            <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground">
              {isHistory ? 'Not enough samples in range' : 'Collecting samples…'}
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
                <CartesianGrid stroke="hsl(var(--border))" vertical={false} strokeDasharray="2 4" />
                <XAxis dataKey="t" hide />
                <YAxis
                  tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                  tickFormatter={(v: number) => v.toFixed(0)}
                  width={40}
                  allowDecimals={false}
                />
                <Tooltip
                  contentStyle={{
                    background: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: 4,
                    fontSize: 11,
                  }}
                  labelFormatter={(t) =>
                    isHistory ? new Date(Number(t)).toLocaleString() : ''
                  }
                  formatter={(value, name) => [
                    `${Number(value).toFixed(2)}/min`,
                    String(name),
                  ]}
                />
                {isHistory ? (
                  <Line
                    type="monotone"
                    dataKey="gc"
                    stroke="hsl(var(--primary))"
                    strokeWidth={1.5}
                    dot={false}
                    isAnimationActive={false}
                    name="GC (aggregate)"
                  />
                ) : (
                  collectors.map((c, idx) => (
                    <Line
                      key={c.name}
                      type="monotone"
                      dataKey={shortName(c.name)}
                      stroke={COLORS[idx % COLORS.length]}
                      strokeWidth={1.5}
                      dot={false}
                      isAnimationActive={false}
                    />
                  ))
                )}
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="flex flex-col gap-2 text-[12px]">
          {isHistory ? (
            <div className="text-muted-foreground text-[11px] leading-relaxed">
              Per-collector split is live-only. Historical view plots the
              aggregate GC event rate from stored snapshots.
            </div>
          ) : (
            collectors.map((c, idx) => {
              const rate = ratesSummary.rates.get(c.name) ?? null;
              const barClass = rateBarClass(c.name, rate);
              return (
                <div key={c.name} className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span
                      className="w-2.5 h-2.5 rounded-sm flex-shrink-0"
                      style={{ background: COLORS[idx % COLORS.length] }}
                    />
                    <span className="text-muted-foreground truncate" title={c.name}>
                      {shortName(c.name)}
                    </span>
                  </div>
                  <span className={`font-mono tabular-nums flex-shrink-0 ${barClass}`}>
                    {formatRate(rate)}/m
                  </span>
                </div>
              );
            })
          )}
          {!isHistory && (
            <div className="border-t border-border mt-1 pt-2 flex flex-col gap-1">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Pause (window)</span>
                <span className="font-mono text-foreground tabular-nums">
                  {ratesSummary.pauseMsWindow.toLocaleString()} ms
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Pause (total)</span>
                <span className="font-mono text-foreground tabular-nums">
                  {ratesSummary.pauseMsTotal.toLocaleString()} ms
                </span>
              </div>
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}

function computeRatesSummary(
  samples: Sample[],
  windowSec: number
): { rates: Map<string, number>; pauseMsWindow: number; pauseMsTotal: number } {
  const rates = new Map<string, number>();
  let pauseMsWindow = 0;
  let pauseMsTotal = 0;

  if (samples.length === 0) {
    return { rates, pauseMsWindow, pauseMsTotal };
  }

  const latest = samples[samples.length - 1];
  for (const t of latest.times.values()) pauseMsTotal += t;

  if (samples.length < 2) {
    return { rates, pauseMsWindow, pauseMsTotal };
  }

  const targetTs = latest.timestamp - windowSec * 1000;
  let earlier = samples[0];
  for (const s of samples) {
    if (s.timestamp >= targetTs) {
      earlier = s;
      break;
    }
  }
  const dtSec = (latest.timestamp - earlier.timestamp) / 1000;
  if (dtSec <= 0) {
    return { rates, pauseMsWindow, pauseMsTotal };
  }

  for (const [name, count] of latest.counts.entries()) {
    const prevCount = earlier.counts.get(name) ?? 0;
    const rate = Math.max(0, ((count - prevCount) / dtSec) * 60);
    rates.set(name, rate);
  }

  for (const [name, time] of latest.times.entries()) {
    const prevTime = earlier.times.get(name) ?? 0;
    pauseMsWindow += Math.max(0, time - prevTime);
  }

  return { rates, pauseMsWindow, pauseMsTotal };
}

function rateBarClass(collectorName: string, rate: number | null): string {
  if (rate == null) return 'text-foreground';
  const lower = collectorName.toLowerCase();
  const isOldOrFull = lower.includes('old') || lower.includes('full') || lower.includes('marksweep');
  if (isOldOrFull && rate >= 1) return 'text-destructive font-semibold';
  if (!isOldOrFull && rate >= 15) return 'text-amber-600 font-semibold';
  return 'text-foreground';
}
