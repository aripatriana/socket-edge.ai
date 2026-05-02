import type { JvmMetrics } from '../../api/jvm.types';
import { Card } from '../dashboard/Card';
import { formatBytes } from '../../lib/format';

/**
 * Non-heap memory card. Replaces the plain ChartCard that left a large
 * empty chart area — shows a stacked bar of constituent non-heap pools
 * (Metaspace, Code Cache, Compressed Class Space, etc.) so operators
 * can see WHICH part of non-heap is growing.
 *
 * Non-heap generally doesn't fluctuate like heap does (mostly metadata
 * that only grows with classloading), so a time-series chart adds little
 * value — a breakdown bar gives more actionable info.
 */

/** Colors to cycle through for each non-heap pool segment. */
const POOL_COLORS = [
  'hsl(var(--primary))',          // red — biggest (usually Metaspace)
  'hsl(var(--accent))',           // blue
  '#b47b00',                      // amber
  'hsl(var(--muted-foreground))', // gray
  '#1a8f4a',                      // green
  '#8b5cf6',                      // purple (6th+ fallback)
];

export function NonHeapCard({ metrics }: { metrics: JvmMetrics }) {
  const nonHeapPools = metrics.pools.filter((p) => p.type === 'NON_HEAP' && p.usedBytes > 0);
  const totalUsed = metrics.nonHeap.usedBytes;

  // Sort pools by used size descending — biggest contributors first.
  const sorted = [...nonHeapPools].sort((a, b) => b.usedBytes - a.usedBytes);

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Non-Heap
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {formatBytes(totalUsed)}
            </span>
          </div>
        </div>
      </div>

      {/* Stacked breakdown bar */}
      {sorted.length > 0 && totalUsed > 0 && (
        <div className="mb-3">
          <div className="flex h-3 rounded overflow-hidden bg-muted">
            {sorted.map((pool, idx) => {
              const pct = (pool.usedBytes / totalUsed) * 100;
              if (pct < 0.3) return null; // too thin to render
              return (
                <div
                  key={pool.name}
                  style={{
                    width: `${pct}%`,
                    background: POOL_COLORS[idx % POOL_COLORS.length],
                  }}
                  title={`${pool.name}: ${formatBytes(pool.usedBytes)} (${pct.toFixed(1)}%)`}
                />
              );
            })}
          </div>
          <div className="flex flex-wrap gap-x-3 gap-y-1 mt-2 text-[10.5px]">
            {sorted.map((pool, idx) => (
              <div key={pool.name} className="flex items-center gap-1.5 min-w-0">
                <span
                  className="w-2 h-2 rounded-sm flex-shrink-0"
                  style={{ background: POOL_COLORS[idx % POOL_COLORS.length] }}
                />
                <span className="text-muted-foreground truncate" title={pool.name}>
                  {shortPoolName(pool.name)}
                </span>
                <span className="font-mono text-foreground tabular-nums flex-shrink-0">
                  {formatBytes(pool.usedBytes)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Totals */}
      <div className="grid grid-cols-2 gap-y-1 gap-x-4 text-[11px] pt-3 border-t border-border">
        <StatRow label="Used" value={formatBytes(metrics.nonHeap.usedBytes)} />
        <StatRow label="Committed" value={formatBytes(metrics.nonHeap.committedBytes)} />
        <StatRow
          label="Max"
          value={metrics.nonHeap.maxBytes > 0 ? formatBytes(metrics.nonHeap.maxBytes) : 'unbounded'}
        />
        <StatRow label="Init" value={formatBytes(metrics.nonHeap.initBytes)} />
      </div>
    </Card>
  );
}

function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-mono text-foreground tabular-nums">{value}</span>
    </div>
  );
}

/** Shorten verbose JVM pool names for the legend. */
function shortPoolName(name: string): string {
  return name
    .replace(/^CodeHeap\s*'?([^']+)'?$/, 'Code: $1')
    .replace(/^Compressed Class Space$/, 'Comp Class')
    .replace(/^Metaspace$/, 'Metaspace');
}
