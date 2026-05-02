import type { SystemMetrics } from '../../api/metrics.types';
import { formatBytes, formatPercent, formatUptime } from '../../lib/format';
import { useMetricHistory } from '../../hooks/useMetricHistory';
import { Sparkline } from './Sparkline';
import { Card } from './Card';

/**
 * Compact system-health bar at the top of the Dashboard.
 * Each item shows label, live value, and a tiny sparkline.
 */
export function HealthStrip({ metrics }: { metrics: SystemMetrics }) {
  const cpuHistory = useMetricHistory(metrics.cpu.systemCpuPercent, 60, metrics.timestamp);
  const memHistory = useMetricHistory(metrics.memory.usedPercent, 60, metrics.timestamp);
  const fdHistory = useMetricHistory(metrics.fileDescriptors.usedPercent, 60, metrics.timestamp);

  return (
    <Card className="flex items-center gap-5 mb-4 px-[18px] py-[10px]">
      <HealthItem
        label="CPU"
        value={formatPercent(metrics.cpu.systemCpuPercent)}
        history={cpuHistory}
        yDomain={[0, 100]}
      />
      <Divider />
      <HealthItem
        label="Memory"
        value={formatPercent(metrics.memory.usedPercent)}
        sub={`${formatBytes(metrics.memory.usedBytes)} / ${formatBytes(metrics.memory.totalBytes)}`}
        history={memHistory}
        color="hsl(var(--accent))"
        yDomain={[0, 100]}
      />
      <Divider />
      <HealthItem
        label="File Descriptors"
        value={
          metrics.fileDescriptors.open != null
            ? metrics.fileDescriptors.open.toLocaleString()
            : 'N/A'
        }
        sub={
          metrics.fileDescriptors.usedPercent != null
            ? `${formatPercent(metrics.fileDescriptors.usedPercent)} used`
            : undefined
        }
        history={fdHistory}
      />
      <Divider />
      <HealthItem
        label="Uptime"
        value={formatUptime(metrics.host.uptimeSeconds)}
        sub={metrics.host.hostname}
      />
      <Divider />
      <HealthItem
        label="CPU Load"
        value={
          metrics.cpu.loadAvg1m != null ? metrics.cpu.loadAvg1m.toFixed(2) : 'N/A'
        }
        sub={
          metrics.cpu.loadAvg1m != null &&
          metrics.cpu.loadAvg5m != null &&
          metrics.cpu.loadAvg15m != null
            ? `${metrics.cpu.loadAvg5m.toFixed(2)} · ${metrics.cpu.loadAvg15m.toFixed(2)} (5m · 15m)`
            : 'Linux only'
        }
      />
    </Card>
  );
}

function HealthItem({
  label,
  value,
  sub,
  history,
  color,
  yDomain,
}: {
  label: string;
  value: string;
  sub?: string;
  history?: { t: number; v: number }[];
  color?: string;
  yDomain?: [number, number];
}) {
  return (
    <div className="flex flex-col gap-1 min-w-0 flex-shrink-0">
      <div className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">
        {label}
      </div>
      <div className="flex items-center gap-2">
        <div className="flex flex-col leading-tight">
          <span className="font-mono text-[15px] font-medium text-foreground tabular-nums">
            {value}
          </span>
          {sub && (
            <span className="text-[10px] text-muted-foreground">{sub}</span>
          )}
        </div>
        {history && history.length >= 2 && (
          <div className="w-16 h-[22px] flex-shrink-0">
            <Sparkline data={history} color={color} yDomain={yDomain} />
          </div>
        )}
      </div>
    </div>
  );
}

function Divider() {
  return <div className="w-px h-7 bg-border flex-shrink-0" />;
}
