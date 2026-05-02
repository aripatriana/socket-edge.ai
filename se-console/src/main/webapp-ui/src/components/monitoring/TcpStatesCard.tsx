import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { Card } from '../dashboard/Card';
import type { TcpStateCounts } from '../../api/network.types';
import { formatNumber } from '../../lib/format';

/**
 * TCP socket state distribution. Donut + legend + thin stacked bar.
 *
 * Colors chosen to signal severity:
 *   - ESTABLISHED (accent blue): active, healthy
 *   - TIME_WAIT (amber): normal closing but high counts = tuning signal
 *   - CLOSE_WAIT (destructive): app not closing sockets — memory leak
 *   - LISTEN (emerald): passive, OK
 *   - SYN (muted-foreground): transitional
 *   - OTHER (muted): remaining states
 *
 * Hints in labels help operator interpretation without needing to know
 * TCP state machine details.
 */
const STATES = [
  {
    key: 'established' as const,
    label: 'ESTABLISHED',
    hint: 'active connections',
    color: 'hsl(var(--accent))',
  },
  {
    key: 'timeWait' as const,
    label: 'TIME_WAIT',
    hint: 'normal closing',
    color: '#b47b00', // amber
  },
  {
    key: 'closeWait' as const,
    label: 'CLOSE_WAIT',
    hint: 'app not closing',
    color: 'hsl(var(--destructive))',
  },
  {
    key: 'listen' as const,
    label: 'LISTEN',
    hint: 'accepting new conns',
    color: '#1a8f4a', // emerald
  },
  {
    key: 'syn' as const,
    label: 'SYN_SENT / SYN_RECV',
    hint: 'handshake',
    color: '#8b5cf6', // purple
  },
  {
    key: 'other' as const,
    label: 'Other',
    hint: 'FIN_WAIT, LAST_ACK, etc.',
    color: 'hsl(var(--muted-foreground))',
  },
];

export function TcpStatesCard({ states }: { states: TcpStateCounts }) {
  // Synthesize "syn" as syn_sent + syn_recv, and "other" as sum of remaining.
  const counts = {
    established: states.established,
    timeWait: states.timeWait,
    closeWait: states.closeWait,
    listen: states.listen,
    syn: states.synSent + states.synRecv,
    other: states.finWait1 + states.finWait2 + states.lastAck + states.closing + states.other,
  };

  const total = states.total;
  const data = STATES.map((s) => ({
    name: s.label,
    value: counts[s.key],
    color: s.color,
  })).filter((d) => d.value > 0);

  // Warning hint for elevated TIME_WAIT — threshold is fuzzy, but > 2,000 or
  // > 50% of sockets tends to indicate a tuning issue in TCP LB workloads.
  const timeWaitRatio = total > 0 ? states.timeWait / total : 0;
  const elevatedTimeWait = states.timeWait > 2000 || timeWaitRatio > 0.5;

  // Sustained CLOSE_WAIT > 0 is always worth noticing (app-level bug or
  // connection pool misbehavior).
  const closeWaitFlag = states.closeWait > 50;

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            TCP Socket States
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {formatNumber(total)}
            </span>
            <span className="ml-1 text-[12px] text-muted-foreground">total sockets</span>
          </div>
        </div>
      </div>

      {total === 0 ? (
        <EmptyMessage>No socket data available</EmptyMessage>
      ) : (
        <>
          <div className="grid grid-cols-[160px_1fr] gap-4 items-center mb-3">
            <div className="relative" style={{ width: 160, height: 160 }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={data}
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={72}
                    paddingAngle={2}
                    dataKey="value"
                    isAnimationActive={false}
                  >
                    {data.map((entry) => (
                      <Cell key={entry.name} fill={entry.color} />
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
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="font-mono text-[18px] font-medium text-foreground tabular-nums">
                  {formatNumber(total)}
                </span>
                <span className="text-[9px] uppercase tracking-wider text-muted-foreground">
                  sockets
                </span>
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              {STATES.map((s) => {
                const count = counts[s.key];
                if (count === 0) return null;
                const pct = total > 0 ? (count / total) * 100 : 0;
                const isTimeWait = s.key === 'timeWait';
                const isCloseWait = s.key === 'closeWait';
                const flagged =
                  (isTimeWait && elevatedTimeWait) || (isCloseWait && closeWaitFlag);
                return (
                  <div key={s.key} className="flex items-center gap-2 text-[12px]">
                    <span
                      className="w-2.5 h-2.5 rounded-sm flex-shrink-0"
                      style={{ background: s.color }}
                    />
                    <div className="flex-1 min-w-0 flex items-baseline gap-2">
                      <span
                        className={flagged ? 'font-semibold' : ''}
                        style={{ color: flagged ? s.color : 'hsl(var(--foreground))' }}
                      >
                        {s.label}
                      </span>
                      <span className="text-[10px] text-muted-foreground truncate">
                        {s.hint}
                      </span>
                    </div>
                    <span className="font-mono text-foreground tabular-nums">
                      {formatNumber(count)}
                    </span>
                    <span className="font-mono text-[10px] text-muted-foreground tabular-nums w-12 text-right">
                      {pct.toFixed(1)}%
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Stacked horizontal bar for quick glance */}
          <div className="flex h-2 rounded overflow-hidden bg-muted mb-1">
            {data.map((d) => {
              const pct = total > 0 ? (d.value / total) * 100 : 0;
              if (pct < 0.3) return null;
              return (
                <div
                  key={d.name}
                  style={{ width: `${pct}%`, background: d.color }}
                  title={`${d.name}: ${formatNumber(d.value)} (${pct.toFixed(1)}%)`}
                />
              );
            })}
          </div>

          {/* Warning hints */}
          {(elevatedTimeWait || closeWaitFlag) && (
            <div className="mt-3 space-y-1.5">
              {elevatedTimeWait && (
                <div className="text-[11px] text-amber-600">
                  <b>Elevated TIME_WAIT</b> — {formatNumber(states.timeWait)} sockets
                  closing normally but high volume can exhaust ephemeral ports.
                  Consider connection pooling or <code className="font-mono bg-muted px-1 rounded">tcp_tw_reuse</code>.
                </div>
              )}
              {closeWaitFlag && (
                <div className="text-[11px] text-destructive">
                  <b>Elevated CLOSE_WAIT</b> — {formatNumber(states.closeWait)} sockets
                  where the app hasn't called close() after peer FIN. Likely an
                  application-level connection leak.
                </div>
              )}
            </div>
          )}
        </>
      )}
    </Card>
  );
}

function EmptyMessage({ children }: { children: string }) {
  return (
    <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground py-8">
      {children}
    </div>
  );
}
