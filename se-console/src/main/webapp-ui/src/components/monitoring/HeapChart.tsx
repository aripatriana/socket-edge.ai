import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartCard, ChartStat } from '../dashboard/ChartCard';
import type { JvmMetrics } from '../../api/jvm.types';
import { useMetricHistory } from '../../hooks/useMetricHistory';
import { formatBytes, formatPercent } from '../../lib/format';
import { heapPctSeriesFromRows, type AnyJvmRow } from '../../lib/snapshotAdapters';

/**
 * Heap usage timeline (used %). Dual-mode live vs historical — see
 * CpuUtilizationChart for the pattern. Accepts rows from either the
 * console's own JVM history table or the engine's (both have the fields
 * this chart reads).
 */
export function HeapChart({
  metrics,
  historyRows,
}: {
  metrics: JvmMetrics;
  historyRows?: AnyJvmRow[];
}) {
  const isHistory = !!historyRows;
  const history = useMetricHistory(metrics.heap.usedPercent, 120, metrics.timestamp);

  const data = isHistory
    ? heapPctSeriesFromRows(historyRows).map((p) => ({ t: p.t, used: p.v }))
    : history.map((p) => ({ t: p.t, used: p.v }));

  return (
    <ChartCard
      title="Heap Usage"
      current={formatPercent(metrics.heap.usedPercent)}
      stats={
        <>
          <ChartStat label="used" value={formatBytes(metrics.heap.usedBytes)} />
          <ChartStat label="committed" value={formatBytes(metrics.heap.committedBytes)} />
          <ChartStat
            label="max"
            value={metrics.heap.maxBytes > 0 ? formatBytes(metrics.heap.maxBytes) : 'unbounded'}
          />
        </>
      }
    >
      {data.length < 2 ? (
        <EmptyMessage>
          {isHistory ? 'No samples in selected range' : 'Collecting samples…'}
        </EmptyMessage>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
            <defs>
              <linearGradient id="heapFill" x1="0" y1="0" x2="0" y2="1">
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
              labelFormatter={(t) =>
                isHistory ? new Date(Number(t)).toLocaleString() : ''
              }
              formatter={(value) => [`${Number(value).toFixed(1)}%`, 'Heap']}
            />
            <Area
              type="monotone"
              dataKey="used"
              stroke="hsl(var(--primary))"
              strokeWidth={1.5}
              fill="url(#heapFill)"
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
