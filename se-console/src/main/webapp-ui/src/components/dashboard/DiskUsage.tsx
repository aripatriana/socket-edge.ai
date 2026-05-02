import type { DiskMetrics } from '../../api/metrics.types';
import { formatBytes, formatPercent } from '../../lib/format';

/**
 * Per-mount disk usage bars. Color shifts from primary (low) to amber (75%+)
 * to destructive (90%+).
 */
export function DiskUsage({ disks }: { disks: DiskMetrics[] }) {
  if (disks.length === 0) {
    return (
      <div className="flex items-center justify-center text-[12px] text-muted-foreground h-[100px]">
        No disks reported
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {disks.map((disk) => (
        <DiskRow key={disk.mount} disk={disk} />
      ))}
    </div>
  );
}

function DiskRow({ disk }: { disk: DiskMetrics }) {
  const pct = disk.usedPercent ?? 0;
  const barClass =
    pct >= 90
      ? 'bg-destructive'
      : pct >= 75
      ? 'bg-amber-500'
      : 'bg-accent';

  return (
    <div>
      <div className="flex items-baseline justify-between gap-2 mb-1">
        <div className="flex items-baseline gap-2 min-w-0">
          <span className="font-mono text-[12px] font-medium text-foreground truncate">
            {disk.mount}
          </span>
          {disk.fsType && (
            <span className="text-[10px] text-muted-foreground">{disk.fsType}</span>
          )}
        </div>
        <div className="flex items-baseline gap-2 flex-shrink-0">
          <span className="font-mono text-[11px] text-muted-foreground">
            {formatBytes(disk.usedBytes)} / {formatBytes(disk.totalBytes)}
          </span>
          <span
            className="font-mono text-[12px] font-medium text-foreground tabular-nums text-right"
            style={{ minWidth: 48 }}
          >
            {formatPercent(disk.usedPercent)}
          </span>
        </div>
      </div>
      <div className="h-2 rounded overflow-hidden bg-muted">
        <div
          className={`h-full transition-all duration-500 ${barClass}`}
          style={{ width: `${Math.min(pct, 100)}%` }}
        />
      </div>
    </div>
  );
}
