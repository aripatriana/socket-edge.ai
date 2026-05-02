import type { SystemMetrics } from '../../api/metrics.types';
import { Card } from '../dashboard/Card';
import { formatBytes, formatPercent } from '../../lib/format';

/**
 * Memory breakdown card for System page.
 * On Linux: shows Used / Cached / Buffers / Free with stacked bar.
 * On Windows/Mac: falls back to Used / Available with note about platform.
 *
 * Cache+buffers distinction matters for Linux operators: the OS reports
 * "used" memory as the difference between total and free, but cached
 * pages are "reclaimable" and count as available pressure-wise.
 */
export function MemoryBreakdownCard({ metrics }: { metrics: SystemMetrics }) {
  const mem = metrics.memory;
  const hasLinuxBreakdown = mem.cachedBytes != null && mem.buffersBytes != null;

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Memory (Physical)
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {formatBytes(mem.usedBytes)}
            </span>
            <span className="ml-1 text-[12px] text-muted-foreground">
              / {formatBytes(mem.totalBytes)}
            </span>
          </div>
        </div>
        <div className="flex gap-3 text-[11px] flex-wrap justify-end text-muted-foreground">
          <Stat label="Used" value={formatPercent(mem.usedPercent)} />
          <Stat label="Free" value={formatBytes(mem.availableBytes)} />
        </div>
      </div>

      {hasLinuxBreakdown ? (
        <LinuxBreakdown mem={mem} />
      ) : (
        <FallbackBreakdown mem={mem} />
      )}
    </Card>
  );
}

function LinuxBreakdown({ mem }: { mem: SystemMetrics['memory'] }) {
  const total = mem.totalBytes;
  const cached = mem.cachedBytes ?? 0;
  const buffers = mem.buffersBytes ?? 0;
  // "Used by apps" = total - free - cached - buffers (classic Linux free(1) semantic)
  const appsUsed = Math.max(0, total - mem.availableBytes - cached - buffers);
  const free = Math.max(0, total - appsUsed - cached - buffers);

  const appsPct = (appsUsed / total) * 100;
  const cachedPct = (cached / total) * 100;
  const buffersPct = (buffers / total) * 100;
  const freePct = (free / total) * 100;

  return (
    <>
      <div className="flex h-3 rounded overflow-hidden bg-muted mb-3">
        <div style={{ width: `${appsPct}%`, background: 'hsl(var(--primary))' }} title={`Apps: ${formatBytes(appsUsed)}`} />
        <div style={{ width: `${cachedPct}%`, background: 'hsl(var(--accent))' }} title={`Cached: ${formatBytes(cached)}`} />
        <div style={{ width: `${buffersPct}%`, background: '#b47b00' }} title={`Buffers: ${formatBytes(buffers)}`} />
        <div style={{ width: `${freePct}%`, background: 'hsl(var(--muted-foreground) / 0.25)' }} title={`Free: ${formatBytes(free)}`} />
      </div>
      <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[11px]">
        <BreakdownRow color="hsl(var(--primary))" label="Apps" value={formatBytes(appsUsed)} />
        <BreakdownRow color="hsl(var(--accent))" label="Cached" value={formatBytes(cached)} />
        <BreakdownRow color="#b47b00" label="Buffers" value={formatBytes(buffers)} />
        <BreakdownRow color="hsl(var(--muted-foreground) / 0.25)" label="Free" value={formatBytes(free)} />
      </div>
    </>
  );
}

function FallbackBreakdown({ mem }: { mem: SystemMetrics['memory'] }) {
  const total = mem.totalBytes;
  const usedPct = total > 0 ? (mem.usedBytes / total) * 100 : 0;
  const freePct = total > 0 ? (mem.availableBytes / total) * 100 : 0;

  return (
    <>
      <div className="flex h-3 rounded overflow-hidden bg-muted mb-3">
        <div style={{ width: `${usedPct}%`, background: 'hsl(var(--primary))' }} title={`Used: ${formatBytes(mem.usedBytes)}`} />
        <div style={{ width: `${freePct}%`, background: 'hsl(var(--muted-foreground) / 0.25)' }} title={`Free: ${formatBytes(mem.availableBytes)}`} />
      </div>
      <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[11px]">
        <BreakdownRow color="hsl(var(--primary))" label="Used" value={formatBytes(mem.usedBytes)} />
        <BreakdownRow color="hsl(var(--muted-foreground) / 0.25)" label="Free" value={formatBytes(mem.availableBytes)} />
      </div>
      <div className="text-[10px] text-muted-foreground mt-2 italic">
        Cached / Buffers breakdown available on Linux only
      </div>
    </>
  );
}

function BreakdownRow({ color, label, value }: { color: string; label: string; value: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className="w-2 h-2 rounded-sm flex-shrink-0" style={{ background: color }} />
      <span className="text-muted-foreground flex-1">{label}</span>
      <span className="font-mono text-foreground tabular-nums">{value}</span>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-1">
      <span>{label}</span>
      <b className="font-mono text-foreground font-medium tabular-nums">{value}</b>
    </div>
  );
}
