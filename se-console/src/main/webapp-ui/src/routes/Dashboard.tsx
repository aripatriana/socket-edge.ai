import { AppShell } from '../components/layout/AppShell';
import { HealthStrip } from '../components/dashboard/HealthStrip';
import { KpiCard } from '../components/dashboard/KpiCard';
import { CpuChart } from '../components/dashboard/CpuChart';
import { MemoryChart } from '../components/dashboard/MemoryChart';
import { DiskUsage } from '../components/dashboard/DiskUsage';
import { ChartCard } from '../components/dashboard/ChartCard';
import { Card } from '../components/dashboard/Card';
import { useSystemMetrics } from '../hooks/useSystemMetrics';
import { useMetricHistory } from '../hooks/useMetricHistory';
import { formatPercent, formatBytes, formatUptime, formatNumber } from '../lib/format';

/**
 * Dashboard — landing page after login. Read-only system monitoring summary.
 */
export function Dashboard() {
  const query = useSystemMetrics();
  const metrics = query.data;
  const lastUpdated = metrics ? new Date(metrics.timestamp) : null;

  // Sparkline histories for KPI cards.
  const cpuHistory = useMetricHistory(metrics?.cpu.systemCpuPercent, 60, metrics?.timestamp);
  const memHistory = useMetricHistory(metrics?.memory.usedPercent, 60, metrics?.timestamp);
  const fdHistory = useMetricHistory(metrics?.fileDescriptors.usedPercent, 60, metrics?.timestamp);
  const loadHistory = useMetricHistory(metrics?.cpu.loadAvg1m ?? undefined, 60, metrics?.timestamp);

  if (query.isLoading || !metrics) {
    return (
      <AppShell>
        <div className="flex items-center justify-center h-[200px] text-muted-foreground">
          Loading metrics…
        </div>
      </AppShell>
    );
  }

  if (query.isError) {
    return (
      <AppShell lastUpdated={lastUpdated}>
        <div className="px-4 py-3 rounded text-[13px] bg-destructive/10 text-destructive border border-destructive">
          Failed to load system metrics. Retrying…
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell lastUpdated={lastUpdated}>
      <HealthStrip metrics={metrics} />

      {/* KPI grid */}
      <div className="grid grid-cols-4 gap-3 mb-4">
        <KpiCard
          label="CPU (system)"
          value={
            metrics.cpu.systemCpuPercent != null
              ? metrics.cpu.systemCpuPercent.toFixed(1)
              : 'N/A'
          }
          unit="%"
          sub={`${metrics.cpu.logicalCores} cores · ${metrics.cpu.physicalCores} physical`}
          history={cpuHistory}
          yDomain={[0, 100]}
        />
        <KpiCard
          label="Memory"
          value={
            metrics.memory.usedPercent != null
              ? metrics.memory.usedPercent.toFixed(1)
              : 'N/A'
          }
          unit="%"
          sub={`${formatBytes(metrics.memory.usedBytes)} / ${formatBytes(metrics.memory.totalBytes)}`}
          history={memHistory}
          yDomain={[0, 100]}
          sparkColor="hsl(var(--accent))"
        />
        <KpiCard
          label="File Descriptors"
          value={
            metrics.fileDescriptors.open != null
              ? formatNumber(metrics.fileDescriptors.open)
              : 'N/A'
          }
          sub={
            metrics.fileDescriptors.max != null
              ? `of ${formatNumber(metrics.fileDescriptors.max)} (${formatPercent(metrics.fileDescriptors.usedPercent)})`
              : 'Unix only'
          }
          history={fdHistory}
        />
        <KpiCard
          label="Load Avg (1m)"
          value={
            metrics.cpu.loadAvg1m != null ? metrics.cpu.loadAvg1m.toFixed(2) : 'N/A'
          }
          sub={
            metrics.cpu.loadAvg5m != null && metrics.cpu.loadAvg15m != null
              ? `${metrics.cpu.loadAvg5m.toFixed(2)} · ${metrics.cpu.loadAvg15m.toFixed(2)} (5m · 15m)`
              : 'Linux only'
          }
          history={loadHistory}
        />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-2 gap-3 mb-4">
        <CpuChart metrics={metrics} />
        <MemoryChart metrics={metrics} />
      </div>

      {/* Disk usage */}
      <div className="mb-4">
        <ChartCard title="Disk Usage" height="auto">
          <DiskUsage disks={metrics.disks} />
        </ChartCard>
      </div>

      {/* Host info footer */}
      <Card className="grid grid-cols-4 gap-4 px-4 py-3.5 text-[11px]">
        <HostField label="Hostname" value={metrics.host.hostname} />
        <HostField label="OS" value={`${metrics.host.osName} ${metrics.host.osVersion}`} />
        <HostField label="Architecture" value={metrics.host.osArch} />
        <HostField label="Uptime" value={formatUptime(metrics.host.uptimeSeconds)} />
      </Card>
    </AppShell>
  );
}

function HostField({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="uppercase tracking-wider font-semibold text-[10px] text-muted-foreground">
        {label}
      </span>
      <span className="font-mono text-[12px] text-foreground tabular-nums">
        {value || 'N/A'}
      </span>
    </div>
  );
}
