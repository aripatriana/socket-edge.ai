import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartCard, ChartStat } from './ChartCard';
import type { SystemMetrics } from '../../api/metrics.types';
import { useMetricHistory } from '../../hooks/useMetricHistory';
import { formatBytes, formatPercent } from '../../lib/format';

export function MemoryChart({ metrics }: { metrics: SystemMetrics }) {
  const history = useMetricHistory(metrics.memory.usedPercent, 120, metrics.timestamp);
  const data = history.map((p) => ({ t: p.t, used: p.v }));

  return (
    <ChartCard
      title="Memory Usage"
      current={formatPercent(metrics.memory.usedPercent)}
      stats={
        <>
          <ChartStat label="used" value={formatBytes(metrics.memory.usedBytes)} />
          <ChartStat label="total" value={formatBytes(metrics.memory.totalBytes)} />
          {metrics.memory.swapTotalBytes > 0 && (
            <ChartStat
              label="swap"
              value={`${formatBytes(metrics.memory.swapUsedBytes)} / ${formatBytes(metrics.memory.swapTotalBytes)}`}
            />
          )}
        </>
      }
    >
      {data.length < 2 ? (
        <EmptyMessage>Collecting samples…</EmptyMessage>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
            <defs>
              <linearGradient id="memFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="hsl(var(--accent))" stopOpacity={0.3} />
                <stop offset="100%" stopColor="hsl(var(--accent))" stopOpacity={0} />
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
              formatter={(value) => [`${Number(value).toFixed(1)}%`, 'Used']}
            />
            <Area
              type="monotone"
              dataKey="used"
              stroke="hsl(var(--accent))"
              strokeWidth={1.5}
              fill="url(#memFill)"
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

function EmptyMessage({ children }: { children: string }) {
  return (
    <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground">
      {children}
    </div>
  );
}
