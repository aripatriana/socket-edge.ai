import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Card } from '../dashboard/Card';
import type { NetworkSnapshotRow } from '../../api/network.types';
import { tcpEstablishedSeriesFromRows } from '../../lib/snapshotAdapters';

/**
 * Historical-only chart: TCP established connection count over time.
 *
 * Only renders when the NetworkPage is in historical mode. Live mode uses
 * TcpStatesCard (donut) which shows the point-in-time state breakdown —
 * that visualisation doesn't work over a time range so we show this
 * timeline instead.
 */
export function TcpEstablishedChart({ rows }: { rows: NetworkSnapshotRow[] }) {
  const data = tcpEstablishedSeriesFromRows(rows);

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            TCP Established — history
          </div>
          <div className="text-[11px] text-muted-foreground">
            {rows.length} samples
          </div>
        </div>
      </div>

      <div style={{ height: 180 }}>
        {data.length < 2 ? (
          <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground">
            Not enough samples in range
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
              <defs>
                <linearGradient id="tcpEst" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.2} />
                  <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="hsl(var(--border))" vertical={false} strokeDasharray="2 4" />
              <XAxis dataKey="t" hide />
              <YAxis
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                allowDecimals={false}
                width={40}
              />
              <Tooltip
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: 4,
                  fontSize: 11,
                }}
                labelFormatter={(t) => new Date(Number(t)).toLocaleString()}
                formatter={(value) => [`${value}`, 'Established']}
              />
              <Area
                type="monotone"
                dataKey="v"
                stroke="hsl(var(--primary))"
                strokeWidth={1.5}
                fill="url(#tcpEst)"
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </Card>
  );
}
