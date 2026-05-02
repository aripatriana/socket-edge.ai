import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { ChartCard } from '../dashboard/ChartCard';
import type { ThreadMetrics } from '../../api/jvm.types';
import { formatNumber } from '../../lib/format';

const STATE_COLORS: Record<string, string> = {
  Runnable: 'hsl(var(--accent))',       // blue — actively running
  Waiting: 'hsl(var(--muted-foreground))',
  'Timed Waiting': 'hsl(var(--muted))',
  Blocked: 'hsl(var(--primary))',       // red — contention, stands out
};

/**
 * Thread state distribution. Donut chart with state counts + total in the
 * center. Helps spot lock contention (high Blocked) or stuck threads (high
 * Waiting with no progress).
 */
export function ThreadStates({ threads }: { threads: ThreadMetrics }) {
  const data = [
    { name: 'Runnable', value: threads.runnableCount ?? 0 },
    { name: 'Waiting', value: threads.waitingCount ?? 0 },
    { name: 'Timed Waiting', value: threads.timedWaitingCount ?? 0 },
    { name: 'Blocked', value: threads.blockedCount ?? 0 },
  ].filter((d) => d.value > 0);

  const total = data.reduce((s, d) => s + d.value, 0);

  return (
    <ChartCard
      title="Thread States"
      current={formatNumber(threads.liveCount)}
      unit={`live · peak ${threads.peakCount}`}
      height={200}
    >
      {total === 0 ? (
        <EmptyMessage>No thread info available</EmptyMessage>
      ) : (
        <div className="flex h-full items-center gap-4">
          <div className="relative flex-shrink-0" style={{ width: 140, height: 140 }}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data}
                  cx="50%"
                  cy="50%"
                  innerRadius={42}
                  outerRadius={65}
                  paddingAngle={2}
                  dataKey="value"
                  isAnimationActive={false}
                >
                  {data.map((entry) => (
                    <Cell key={entry.name} fill={STATE_COLORS[entry.name] ?? 'hsl(var(--muted))'} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    background: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: 4,
                    fontSize: 11,
                  }}
                  formatter={(value, name) => [formatNumber(Number(value)), String(name)]}
                />
              </PieChart>
            </ResponsiveContainer>
            {/* Center label */}
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <span className="font-mono text-[18px] font-medium text-foreground tabular-nums">
                {total}
              </span>
              <span className="text-[9px] uppercase tracking-wider text-muted-foreground">
                threads
              </span>
            </div>
          </div>

          {/* Legend */}
          <div className="flex flex-col gap-1.5 flex-1">
            {data.map((entry) => (
              <div key={entry.name} className="flex items-center justify-between text-[12px]">
                <div className="flex items-center gap-2">
                  <span
                    className="w-2.5 h-2.5 rounded-sm flex-shrink-0"
                    style={{ background: STATE_COLORS[entry.name] ?? 'hsl(var(--muted))' }}
                  />
                  <span className="text-muted-foreground">{entry.name}</span>
                </div>
                <span className="font-mono text-foreground tabular-nums">
                  {formatNumber(entry.value)}
                </span>
              </div>
            ))}
          </div>
        </div>
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
