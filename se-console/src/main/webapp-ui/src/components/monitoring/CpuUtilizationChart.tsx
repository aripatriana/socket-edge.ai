import { useMemo } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Card } from '../dashboard/Card';
import type { SystemMetrics, SystemSnapshotRow } from '../../api/metrics.types';
import { useMetricHistory } from '../../hooks/useMetricHistory';
import { formatPercent } from '../../lib/format';
import { cpuSeriesFromRows } from '../../lib/snapshotAdapters';

/**
 * Full-width CPU utilization chart. Dual-mode:
 *  - Live: uses `useMetricHistory` rolling buffer driven by the 2s poll.
 *  - Historical: plots rows from /api/console/system/history directly.
 *
 * Header stats (load averages, core count) always reflect the LATEST
 * live metrics — historical mode doesn't re-render the header every
 * sample. Process CPU legend shows the latest value too.
 */
export function CpuUtilizationChart({
  metrics,
  historyRows,
  seedRows,
  preset = 'live',
}: {
  metrics: SystemMetrics;
  historyRows?: SystemSnapshotRow[];
  seedRows?: SystemSnapshotRow[];
  preset?: 'live' | '1h' | '6h' | '24h';
}) {
  const isHistory = !!historyRows;

  // Live buffers — always populated regardless of mode, so switching
  // Live↔Historical doesn't reset the Live backlog.
  const sysHistory = useMetricHistory(metrics.cpu.systemCpuPercent, 120, metrics.timestamp);
  const procHistory = useMetricHistory(metrics.cpu.processCpuPercent, 120, metrics.timestamp);

  const data = isHistory
    ? cpuSeriesFromRows(historyRows).map((p) => ({
        t: p.t,
        process: p.primary,
        system: p.secondary,
      }))
    : buildLiveSeries(seedRows, sysHistory, procHistory);

  const hasProcess = data.some((d) => d.process != null);
  const stats = computeStats(data.map((d) => d.system).filter((v): v is number => v != null));

  // Interval tick ditentukan dari preset, bukan dari range data aktual,
  // agar tetap konsisten meski DB belum terisi penuh.
  const xTicks = useMemo(() => {
    if (data.length < 2) return [];
    const intervalMs =
      preset === '24h' ? 60 * 60_000 :
      preset === '6h'  ? 30 * 60_000 :
      preset === '1h'  ? 10 * 60_000 :
                             60_000;
    const start = data[0].t;
    const end   = data[data.length - 1].t;
    const first = Math.ceil(start / intervalMs) * intervalMs;
    const result: number[] = [];
    for (let t = first; t <= end; t += intervalMs) result.push(t);
    return result;
  }, [data, preset]);

  const xTickFormatter = (t: number) =>
    new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            CPU Utilization
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {formatPercent(metrics.cpu.systemCpuPercent)}
            </span>
          </div>
        </div>
        <div className="flex gap-3 text-[11px] flex-wrap justify-end text-muted-foreground">
          {stats && (
            <>
              <Stat label="min" value={`${stats.min.toFixed(1)}%`} />
              <Stat label="avg" value={`${stats.avg.toFixed(1)}%`} />
              <Stat label="max" value={`${stats.max.toFixed(1)}%`} />
            </>
          )}
          <Stat label="Cores" value={`${metrics.cpu.logicalCores}`} />
          {metrics.cpu.loadAvg1m != null && (
            <>
              <Stat label="Load 1m" value={metrics.cpu.loadAvg1m.toFixed(2)} />
              <Stat label="5m" value={metrics.cpu.loadAvg5m?.toFixed(2) ?? 'N/A'} />
              <Stat label="15m" value={metrics.cpu.loadAvg15m?.toFixed(2) ?? 'N/A'} />
            </>
          )}
        </div>
      </div>

      <div style={{ height: 220 }}>
        {data.length < 2 ? (
          <EmptyMessage>
            {isHistory ? 'No samples in selected range' : 'Collecting samples…'}
          </EmptyMessage>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 5, right: 10, left: -15, bottom: 0 }}>
              <defs>
                <linearGradient id="cpuProcess" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.2} />
                  <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="hsl(var(--border))" vertical={false} strokeDasharray="2 4" />
              <XAxis
                dataKey="t"
                type="number"
                domain={['dataMin', 'dataMax']}
                scale="time"
                ticks={xTicks}
                tickFormatter={xTickFormatter}
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                domain={[0, 100]}
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                tickFormatter={(v: number) => `${v}%`}
                width={45}
              />
              <Tooltip
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: 4,
                  fontSize: 11,
                }}
                labelFormatter={(t) =>
                  isHistory
                    ? new Date(Number(t)).toLocaleString()
                    : new Date(Number(t)).toLocaleTimeString()
                }
                formatter={(value, name) => [
                  `${Number(value).toFixed(1)}%`,
                  name === 'system' ? 'System' : 'Process',
                ]}
              />
              <Area
                type="monotone"
                dataKey="system"
                stroke="hsl(var(--muted-foreground))"
                strokeWidth={1.5}
                strokeDasharray="4 3"
                fill="transparent"
                isAnimationActive={false}
                connectNulls={false}
              />
              {hasProcess && (
                <Area
                  type="monotone"
                  dataKey="process"
                  stroke="hsl(var(--primary))"
                  strokeWidth={1.8}
                  fill="url(#cpuProcess)"
                  isAnimationActive={false}
                  connectNulls={false}
                />
              )}
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="flex gap-4 mt-3 text-[11px]">
        <LegendItem
          color="hsl(var(--primary))"
          label="Process CPU"
          value={formatPercent(metrics.cpu.processCpuPercent)}
        />
        <LegendItem
          color="hsl(var(--muted-foreground))"
          label="System CPU"
          value={formatPercent(metrics.cpu.systemCpuPercent)}
          dashed
        />
      </div>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-1">
      <span>{label}</span>
      <b className="font-mono text-foreground font-medium tabular-nums">{value}</b>
    </div>
  );
}

function LegendItem({
  color,
  label,
  value,
  dashed,
}: {
  color: string;
  label: string;
  value: string;
  dashed?: boolean;
}) {
  return (
    <div className="flex items-center gap-2">
      <span
        className="w-3 h-0.5"
        style={{
          background: dashed ? 'transparent' : color,
          borderTop: dashed ? `2px dashed ${color}` : undefined,
        }}
      />
      <span className="text-muted-foreground">{label}</span>
      <span className="font-mono text-foreground tabular-nums">{value}</span>
    </div>
  );
}

function EmptyMessage({ children }: { children: string }) {
  return (
    <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground">
      {children}
    </div>
  );
}

/**
 * Builds the live data series by merging DB seed rows (fetched on mount)
 * with the rolling live buffer. Seed rows provide immediate historical
 * context; live buffer points newer than the last seed row are appended
 * on top. Combined result is capped at 120 points.
 */
function buildLiveSeries(
  seedRows: SystemSnapshotRow[] | undefined,
  sysHistory: { t: number; v: number }[],
  procHistory: { t: number; v: number }[]
): { t: number; system: number | null; process: number | null }[] {
  const seedPoints = seedRows
    ? cpuSeriesFromRows(seedRows).map((p) => ({
        t: p.t,
        system: p.secondary,
        process: p.primary,
      }))
    : [];

  const lastSeedT = seedPoints.length > 0 ? seedPoints[seedPoints.length - 1].t : 0;

  const filteredSys = sysHistory.filter((p) => p.t > lastSeedT);
  const filteredProc = procHistory.filter((p) => p.t > lastSeedT);
  const livePoints = filteredSys.map((p, i) => ({
    t: p.t,
    system: p.v as number | null,
    process: filteredProc[i]?.v ?? null,
  }));

  return [...seedPoints, ...livePoints].slice(-120);
}

function computeStats(values: number[]): { min: number; avg: number; max: number } | null {
  if (values.length === 0) return null;
  let min = Infinity;
  let max = -Infinity;
  let sum = 0;
  for (const v of values) {
    if (v < min) min = v;
    if (v > max) max = v;
    sum += v;
  }
  return { min, avg: sum / values.length, max };
}
