import type { SystemMetrics } from '../../api/metrics.types';
import { Card } from '../dashboard/Card';
import { formatNumber, formatPercent } from '../../lib/format';

/**
 * File descriptors card — critical metric for a TCP load balancer since
 * each socket consumes an FD. FD exhaustion = cannot accept new connections.
 *
 * Unix only; Windows gets a "not available" placeholder.
 */
export function FileDescriptorsCard({ metrics }: { metrics: SystemMetrics }) {
  const fd = metrics.fileDescriptors;

  if (fd.open == null || fd.max == null) {
    return (
      <Card className="px-4 py-3.5">
        <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
          File Descriptors
        </div>
        <div className="flex items-center justify-center h-[140px] text-[13px] text-muted-foreground">
          Unix / Linux only
        </div>
      </Card>
    );
  }

  const pct = fd.usedPercent ?? 0;
  const barClass =
    pct >= 90 ? 'bg-destructive' : pct >= 75 ? 'bg-amber-500' : 'bg-accent';

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            File Descriptors
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {formatNumber(fd.open)}
            </span>
            <span className="ml-1 text-[12px] text-muted-foreground">
              / {formatNumber(fd.max)}
            </span>
          </div>
        </div>
        <div className="text-[11px]">
          <span className="text-muted-foreground">Usage </span>
          <b className="font-mono text-foreground font-medium tabular-nums">
            {formatPercent(fd.usedPercent)}
          </b>
        </div>
      </div>

      <div className="h-3 rounded overflow-hidden bg-muted mb-2">
        <div
          className={`h-full transition-all duration-500 ${barClass}`}
          style={{ width: `${Math.min(pct, 100)}%` }}
        />
      </div>

      <div className="text-[11px] text-muted-foreground mt-2">
        {pct >= 90 && (
          <span className="text-destructive font-medium">
            Critical — approaching system limit
          </span>
        )}
        {pct >= 75 && pct < 90 && (
          <span className="text-amber-600 font-medium">
            Elevated — monitor for leaks
          </span>
        )}
        {pct < 75 && <span>Within normal range</span>}
      </div>
    </Card>
  );
}
