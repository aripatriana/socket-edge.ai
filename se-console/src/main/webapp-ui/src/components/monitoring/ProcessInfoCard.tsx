import type { SystemMetrics } from '../../api/metrics.types';
import { Card } from '../dashboard/Card';
import { formatBytes, formatNumber, formatUptime } from '../../lib/format';

/**
 * Process Info card — facts about the SE-Console JVM process itself.
 * Complements SystemInfo (host) and JVM tab (heap, threads internal).
 *
 * Useful for operators who need to find the process quickly (PID),
 * verify it's running as the expected user, or check RSS growth
 * independent of JVM heap (RSS includes native memory + metaspace).
 */
export function ProcessInfoCard({ metrics }: { metrics: SystemMetrics }) {
  const p = metrics.process;

  return (
    <Card className="px-4 py-3.5">
      <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
        Process Info
      </div>
      <div className="grid grid-cols-1 gap-y-1.5 text-[11px]">
        <Row label="PID" value={p.pid != null ? String(p.pid) : 'N/A'} mono />
        <Row label="Process name" value={p.processName ?? 'N/A'} mono />
        <Row label="User" value={p.user ?? 'N/A'} mono />
        <Row
          label="Working dir"
          value={p.workingDirectory ?? 'N/A'}
          mono
          truncate
        />
        <Row
          label="Start time"
          value={p.startTime ? formatStartTime(p.startTime) : 'N/A'}
          mono
        />
        <Row
          label="Process uptime"
          value={p.uptimeSeconds != null ? formatUptime(p.uptimeSeconds) : 'N/A'}
        />
        <Row
          label="RSS memory"
          value={p.residentSetSizeBytes != null ? formatBytes(p.residentSetSizeBytes) : 'N/A'}
          mono
        />
        <Row
          label="Virtual memory"
          value={p.virtualMemorySizeBytes != null ? formatBytes(p.virtualMemorySizeBytes) : 'N/A'}
          mono
        />
        <Row
          label="Thread count"
          value={p.threadCount != null ? formatNumber(p.threadCount) : 'N/A'}
          mono
        />
        {p.openFileCount != null && p.openFileCount > 0 && (
          <Row label="Open files" value={formatNumber(p.openFileCount)} mono />
        )}
      </div>
    </Card>
  );
}

function Row({
  label,
  value,
  mono,
  truncate,
}: {
  label: string;
  value: string;
  mono?: boolean;
  truncate?: boolean;
}) {
  return (
    <div className="flex items-baseline justify-between gap-2 min-w-0">
      <span className="text-muted-foreground flex-shrink-0">{label}</span>
      <span
        className={`text-right text-foreground ${mono ? 'font-mono tabular-nums' : ''} ${
          truncate ? 'truncate min-w-0' : ''
        }`}
        title={truncate ? value : undefined}
      >
        {value}
      </span>
    </div>
  );
}

/** Format ISO timestamp as "YYYY-MM-DD HH:mm:ss" (local time). */
function formatStartTime(iso: string): string {
  try {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  } catch {
    return iso;
  }
}
