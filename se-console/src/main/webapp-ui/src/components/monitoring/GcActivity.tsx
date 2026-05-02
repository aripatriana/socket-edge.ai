import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { ChartCard } from '../dashboard/ChartCard';
import type { GcCollector } from '../../api/jvm.types';
import { formatNumber } from '../../lib/format';

/**
 * GC activity — for each collector, show count + time. Bar chart here is
 * "count of collections per collector" (a steady ratio tells you which
 * generation is churning). Times appear in the tooltip.
 */
export function GcActivity({ collectors }: { collectors: GcCollector[] }) {
  const data = collectors.map((c) => ({
    name: shortName(c.name),
    count: c.collectionCount,
    timeMs: c.collectionTimeMs,
    full: c.name,
  }));

  const totalCount = collectors.reduce((s, c) => s + c.collectionCount, 0);
  const totalTime = collectors.reduce((s, c) => s + c.collectionTimeMs, 0);

  return (
    <ChartCard
      title="GC Collections"
      current={formatNumber(totalCount)}
      unit={`· ${totalTime.toLocaleString()} ms total`}
    >
      {data.length === 0 ? (
        <EmptyMessage>No GC collectors reported</EmptyMessage>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
            <CartesianGrid stroke="hsl(var(--border))" vertical={false} strokeDasharray="2 4" />
            <XAxis
              dataKey="name"
              tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
            />
            <YAxis
              tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
              width={40}
            />
            <Tooltip
              contentStyle={{
                background: 'hsl(var(--card))',
                border: '1px solid hsl(var(--border))',
                borderRadius: 4,
                fontSize: 11,
              }}
              cursor={{ fill: 'hsl(var(--muted))' }}
              formatter={(value, _name, item) => {
                const payload = item?.payload as { timeMs?: number; full?: string };
                return [
                  `${formatNumber(Number(value))} collections · ${payload?.timeMs?.toLocaleString() ?? 0} ms`,
                  payload?.full ?? '',
                ];
              }}
            />
            <Bar dataKey="count" fill="hsl(var(--accent))" isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}

/** Shorten long collector names for the X axis (e.g. "G1 Young Generation" → "G1 Young"). */
function shortName(name: string): string {
  return name
    .replace(/Generation/i, '')
    .replace(/Collector/i, '')
    .trim();
}

function EmptyMessage({ children }: { children: string }) {
  return (
    <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground">
      {children}
    </div>
  );
}
