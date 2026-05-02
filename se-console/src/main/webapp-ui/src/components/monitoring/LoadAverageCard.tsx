import type { SystemMetrics } from '../../api/metrics.types';
import { Card } from '../dashboard/Card';

/**
 * Load average card — Linux only metric that tells you how many processes
 * are runnable or in uninterruptible sleep. Rule of thumb: load should
 * stay below the core count; sustained load above that indicates
 * oversubscription.
 */
export function LoadAverageCard({ metrics }: { metrics: SystemMetrics }) {
  const { loadAvg1m, loadAvg5m, loadAvg15m, logicalCores } = metrics.cpu;

  if (loadAvg1m == null) {
    return (
      <Card className="px-4 py-3.5">
        <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
          Load Average
        </div>
        <div className="flex items-center justify-center h-[140px] text-[13px] text-muted-foreground">
          Linux only
        </div>
      </Card>
    );
  }

  // Load / cores ratio — above 1.0 means oversubscribed.
  const ratio = loadAvg1m / logicalCores;
  const pct = Math.min(ratio, 1.5) / 1.5 * 100; // cap visual at 150%
  const barClass =
    ratio >= 1.0 ? 'bg-destructive' : ratio >= 0.7 ? 'bg-amber-500' : 'bg-accent';

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Load Average
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {loadAvg1m.toFixed(2)}
            </span>
            <span className="ml-1 text-[12px] text-muted-foreground">
              / {loadAvg5m?.toFixed(2) ?? 'N/A'} / {loadAvg15m?.toFixed(2) ?? 'N/A'}
            </span>
          </div>
        </div>
        <div className="text-[11px] text-right">
          <div className="text-muted-foreground">Cores</div>
          <b className="font-mono text-foreground font-medium tabular-nums">
            {logicalCores}
          </b>
        </div>
      </div>

      <div className="relative h-3 rounded overflow-hidden bg-muted mb-2">
        <div
          className={`h-full transition-all duration-500 ${barClass}`}
          style={{ width: `${pct}%` }}
        />
        {/* Capacity line at cores-count mark (=1.0 ratio = ~67% of visual scale) */}
        <div
          className="absolute top-0 bottom-0 border-l border-dashed border-destructive"
          style={{ left: `${(1.0 / 1.5) * 100}%` }}
          title={`Capacity line: load = ${logicalCores} cores`}
        />
      </div>

      <div className="flex justify-between text-[10px] text-muted-foreground">
        <span>1m · 5m · 15m</span>
        <span>
          {ratio >= 1.0 ? (
            <span className="text-destructive font-medium">
              Above capacity ({(ratio * 100).toFixed(0)}%)
            </span>
          ) : ratio >= 0.7 ? (
            <span className="text-amber-600 font-medium">
              Elevated ({(ratio * 100).toFixed(0)}% of cores)
            </span>
          ) : (
            <span>Normal ({(ratio * 100).toFixed(0)}% of cores)</span>
          )}
        </span>
      </div>
    </Card>
  );
}
