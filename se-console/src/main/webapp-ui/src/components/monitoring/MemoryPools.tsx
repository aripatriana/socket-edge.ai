import type { MemoryPool } from '../../api/jvm.types';
import { Card } from '../dashboard/Card';
import { formatBytes, formatPercent } from '../../lib/format';

/**
 * Memory pools table. Shows each MemoryPoolMXBean — typically Eden,
 * Survivor, Old Gen, Metaspace, Code Cache, Compressed Class Space.
 * Each row has a small bar to visualize usage at a glance.
 */
export function MemoryPools({ pools }: { pools: MemoryPool[] }) {
  return (
    <Card className="px-4 py-3.5">
      <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
        Memory Pools
      </div>
      <div className="flex flex-col gap-2.5">
        {pools.map((pool) => (
          <PoolRow key={pool.name} pool={pool} />
        ))}
      </div>
    </Card>
  );
}

function PoolRow({ pool }: { pool: MemoryPool }) {
  const pct = pool.usedPercent ?? 0;
  const unbounded = pool.maxBytes <= 0;
  const barClass =
    pct >= 90 ? 'bg-destructive' : pct >= 75 ? 'bg-amber-500' : 'bg-accent';

  return (
    <div>
      <div className="flex items-baseline justify-between gap-2 mb-1">
        <div className="flex items-baseline gap-2 min-w-0">
          <span className="font-mono text-[12px] font-medium text-foreground truncate">
            {pool.name}
          </span>
          <span
            className="text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded"
            style={{
              background: pool.type === 'HEAP' ? 'hsl(var(--primary) / 0.1)' : 'hsl(var(--muted))',
              color: pool.type === 'HEAP' ? 'hsl(var(--primary))' : 'hsl(var(--muted-foreground))',
            }}
          >
            {pool.type === 'HEAP' ? 'Heap' : 'Non-heap'}
          </span>
        </div>
        <div className="flex items-baseline gap-2 flex-shrink-0">
          <span className="font-mono text-[11px] text-muted-foreground">
            {formatBytes(pool.usedBytes)} / {unbounded ? '∞' : formatBytes(pool.maxBytes)}
          </span>
          <span
            className="font-mono text-[12px] font-medium text-foreground tabular-nums text-right"
            style={{ minWidth: 48 }}
          >
            {unbounded ? '—' : formatPercent(pool.usedPercent)}
          </span>
        </div>
      </div>
      {!unbounded && (
        <div className="h-1.5 rounded overflow-hidden bg-muted">
          <div
            className={`h-full transition-all duration-500 ${barClass}`}
            style={{ width: `${Math.min(pct, 100)}%` }}
          />
        </div>
      )}
    </div>
  );
}
