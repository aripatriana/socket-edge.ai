import type { SystemMetrics } from '../../api/metrics.types';
import { Card } from '../dashboard/Card';
import { formatBytes, formatUptime } from '../../lib/format';

/**
 * System Info — host-level facts about the machine. Static-ish data that
 * won't change within a session. Separated from ProcessInfo so operators
 * can distinguish "machine" vs "process" identity.
 */
export function SystemInfoCard({ metrics }: { metrics: SystemMetrics }) {
  const host = metrics.host;
  const cpu = metrics.cpu;
  const mem = metrics.memory;

  return (
    <Card className="px-4 py-3.5">
      <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
        System Info
      </div>
      <div className="grid grid-cols-1 gap-y-1.5 text-[11px]">
        <Row label="Hostname" value={host.hostname || 'N/A'} mono />
        <Row label="OS" value={`${host.osName} ${host.osVersion}`.trim()} />
        <Row label="Architecture" value={host.osArch} mono />
        <Row label="CPU Model" value={cpu.model} mono truncate />
        <Row
          label="CPU Cores"
          value={`${cpu.logicalCores} (logical) / ${cpu.physicalCores} (physical)`}
        />
        <Row label="Total Memory" value={formatBytes(mem.totalBytes)} mono />
        <Row label="System Uptime" value={formatUptime(host.uptimeSeconds)} />
        {host.bootTime && (
          <Row label="Booted" value={formatBootTime(host.bootTime)} mono />
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

function formatBootTime(iso: string): string {
  try {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch {
    return iso;
  }
}
