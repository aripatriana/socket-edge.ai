import type { SystemMetrics } from '../../api/metrics.types';
import { Card } from '../dashboard/Card';
import { formatBytes, formatPercent } from '../../lib/format';

/**
 * Swap usage card. For well-tuned production servers, swap should be
 * near zero — any sustained swap activity is a red flag.
 *
 * Design decision: no timeline here (would want history). Just current
 * state with status message. Phase 5 adds swap-in/out rate timeline.
 */
export function SwapCard({ metrics }: { metrics: SystemMetrics }) {
  const mem = metrics.memory;
  const total = mem.swapTotalBytes;
  const used = mem.swapUsedBytes;

  if (total === 0) {
    return (
      <Card className="px-4 py-3.5">
        <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
          Swap
        </div>
        <div className="flex items-center justify-center h-[140px] text-[13px] text-muted-foreground">
          No swap configured
        </div>
      </Card>
    );
  }

  const usedPct = (used / total) * 100;
  const healthy = used === 0;
  const warning = usedPct >= 20 && usedPct < 50;
  const critical = usedPct >= 50;

  const statusText = healthy
    ? 'No swap activity — healthy'
    : critical
    ? 'High swap usage — memory pressure'
    : warning
    ? 'Moderate swap usage'
    : 'Minor swap usage';

  const statusColor = healthy
    ? 'text-emerald-600'
    : critical
    ? 'text-destructive'
    : warning
    ? 'text-amber-600'
    : 'text-muted-foreground';

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Swap
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {formatBytes(used)}
            </span>
            <span className="ml-1 text-[12px] text-muted-foreground">
              / {formatBytes(total)}
            </span>
          </div>
        </div>
        <div className="text-[11px] text-muted-foreground">
          <div>
            <span>Used </span>
            <b className="font-mono text-foreground font-medium tabular-nums">
              {formatPercent(usedPct)}
            </b>
          </div>
        </div>
      </div>

      <div className="h-2 rounded overflow-hidden bg-muted mb-3">
        <div
          className={`h-full transition-all duration-500 ${
            healthy ? 'bg-emerald-500' : critical ? 'bg-destructive' : warning ? 'bg-amber-500' : 'bg-accent'
          }`}
          style={{ width: `${Math.min(usedPct, 100)}%` }}
        />
      </div>

      <div className={`text-center text-[12px] font-medium ${statusColor} mt-4`}>
        {statusText}
      </div>
    </Card>
  );
}
