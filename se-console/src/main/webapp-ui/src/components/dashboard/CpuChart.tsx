import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartCard, ChartStat } from './ChartCard';
import type { SystemMetrics } from '../../api/metrics.types';
import { useMetricHistory } from '../../hooks/useMetricHistory';
import { formatPercent } from '../../lib/format';

export function CpuChart({ metrics }: { metrics: SystemMetrics }) {
  const sysHistory = useMetricHistory(metrics.cpu.systemCpuPercent, 120, metrics.timestamp);
  const procHistory = useMetricHistory(metrics.cpu.processCpuPercent, 120, metrics.timestamp);

  // Build series. System is always plotted. Process is plotted only if the
  // JVM exposes getProcessCpuLoad (may be null on some platforms/JVMs —
  // e.g. when the Sun-internal OperatingSystemMXBean isn't reachable).
  const hasProcess = procHistory.length >= 2;
  const data = sysHistory.map((p, i) => ({
    t: p.t,
    system: p.v,
    process: hasProcess && procHistory[i] ? procHistory[i].v : null,
  }));

  const stats = computeStats(sysHistory.map((p) => p.v));

  return (
    <ChartCard
      title="CPU Usage"
      current={formatPercent(metrics.cpu.systemCpuPercent)}
      stats={
        stats && (
          <>
            <ChartStat label="min" value={`${stats.min.toFixed(1)}%`} />
            <ChartStat label="avg" value={`${stats.avg.toFixed(1)}%`} />
            <ChartStat label="max" value={`${stats.max.toFixed(1)}%`} />
          </>
        )
      }
    >
      {data.length < 2 ? (
        <EmptyMessage>Collecting samples…</EmptyMessage>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
            <defs>
              <linearGradient id="cpuSystem" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.25} />
                <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="hsl(var(--border))" vertical={false} strokeDasharray="2 4" />
            <XAxis dataKey="t" hide />
            <YAxis
              domain={[0, 100]}
              tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
              tickFormatter={(v: number) => `${v}%`}
              width={40}
            />
            <Tooltip
              contentStyle={{
                background: 'hsl(var(--card))',
                border: '1px solid hsl(var(--border))',
                borderRadius: 4,
                fontSize: 11,
              }}
              labelFormatter={() => ''}
              formatter={(value, name) => [
                `${Number(value).toFixed(1)}%`,
                name === 'system' ? 'System' : 'Process',
              ]}
            />
            <Area
              type="monotone"
              dataKey="system"
              stroke="hsl(var(--primary))"
              strokeWidth={1.5}
              fill="url(#cpuSystem)"
              isAnimationActive={false}
            />
            {hasProcess && (
              <Area
                type="monotone"
                dataKey="process"
                stroke="hsl(var(--muted-foreground))"
                strokeWidth={1.5}
                fill="transparent"
                isAnimationActive={false}
              />
            )}
          </AreaChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
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

function EmptyMessage({ children }: { children: string }) {
  return (
    <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground">
      {children}
    </div>
  );
}
