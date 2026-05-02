import type { ListeningPort } from '../../api/network.types';
import { Card } from '../dashboard/Card';
import { formatNumber } from '../../lib/format';

/**
 * Listening ports table — bind address + port + connection count.
 *
 * Chat 3c will extend this table with "Channel" column (from engine JMX)
 * and per-port Accepts/sec + Rate columns. Layout reserves space for
 * those additions via a comment-only placeholder; no UI change needed now.
 */
export function ListeningPortsCard({ ports }: { ports: ListeningPort[] }) {
  const sorted = [...ports].sort((a, b) => a.port - b.port);

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Listening Ports
          </div>
          <div className="text-[11px] text-muted-foreground">
            {formatNumber(sorted.length)} listeners · sum{' '}
            {formatNumber(sorted.reduce((s, p) => s + p.establishedCount, 0))} established
          </div>
        </div>
      </div>

      {sorted.length === 0 ? (
        <div className="flex items-center justify-center h-24 text-[12px] text-muted-foreground">
          No listening ports reported
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-[12px]">
            <thead>
              <tr className="border-b border-border text-[10px] uppercase tracking-wider text-muted-foreground">
                <th className="text-left font-semibold py-2 px-2">Bind</th>
                <th className="text-left font-semibold py-2 px-2 w-16">Proto</th>
                {/* Chat 3c will insert "Channel" column here after engine JMX. */}
                <th className="text-right font-semibold py-2 px-2 w-28">Established</th>
                <th className="text-right font-semibold py-2 px-2 w-20">PID</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((p, i) => (
                <tr
                  key={`${p.protocol}-${p.localAddress}-${p.port}-${i}`}
                  className="border-b border-border hover:bg-muted/50"
                >
                  <td className="py-1.5 px-2 font-mono text-foreground">
                    {displayAddress(p.localAddress)}:{p.port}
                  </td>
                  <td className="py-1.5 px-2 font-mono text-[11px] text-muted-foreground">
                    {p.protocol}
                  </td>
                  <td className="py-1.5 px-2 font-mono text-right text-foreground tabular-nums">
                    {formatNumber(p.establishedCount)}
                  </td>
                  <td className="py-1.5 px-2 font-mono text-right text-muted-foreground tabular-nums">
                    {p.pid ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

/** "0.0.0.0" / "::" stays as-is. Other addresses too. */
function displayAddress(addr: string): string {
  if (!addr) return '0.0.0.0';
  return addr;
}
