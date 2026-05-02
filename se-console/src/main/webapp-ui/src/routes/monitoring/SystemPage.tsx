import { useSystemMetrics } from '../../hooks/useSystemMetrics';
import { useSystemHistory } from '../../hooks/useSystemHistory';
import { CpuUtilizationChart } from '../../components/monitoring/CpuUtilizationChart';
import { MemoryBreakdownCard } from '../../components/monitoring/MemoryBreakdownCard';
import { SwapCard } from '../../components/monitoring/SwapCard';
import { LoadAverageCard } from '../../components/monitoring/LoadAverageCard';
import { FileDescriptorsCard } from '../../components/monitoring/FileDescriptorsCard';
import { DiskUsage } from '../../components/dashboard/DiskUsage';
import { ChartCard } from '../../components/dashboard/ChartCard';
import { SystemInfoCard } from '../../components/monitoring/SystemInfoCard';
import { ProcessInfoCard } from '../../components/monitoring/ProcessInfoCard';
import {
  TimeRangeSelector,
  useTimeRange,
} from '../../components/shared/TimeRangeSelector';

/**
 * Monitoring → System page. Deep-dive OS-level metrics.
 *
 * Time range selector:
 *  - "Live": chart components use their built-in rolling buffer
 *    (useMetricHistory) fed by the 2s poll.
 *  - "1h / 6h / 24h": fetches console_system_snapshot rows via
 *    /api/console/system/history and passes them to each chart as
 *    `historyRows` — the chart renders from stored data instead of the
 *    rolling buffer.
 */
export function SystemPage() {
  const { preset, setPreset, from, to, isLive } = useTimeRange('live');

  // Latest snapshot — always polls, regardless of range selection. It
  // backs every card's "current value" header and the info/process panels
  // which are point-in-time only.
  const live = useSystemMetrics();
  const history = useSystemHistory(from, to);

  const metrics = live.data;

  if (live.isLoading || !metrics) {
    return (
      <div className="flex items-center justify-center h-[200px] text-muted-foreground">
        Loading system metrics…
      </div>
    );
  }

  if (live.isError) {
    return (
      <div className="px-4 py-3 rounded text-[13px] bg-destructive/10 text-destructive border border-destructive">
        Failed to load system metrics. Retrying…
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-[14px] font-semibold text-foreground m-0">
          System
        </h2>
        <TimeRangeSelector value={preset} onChange={setPreset} />
      </div>

      {!isLive && (
        <HistoryBanner
          preset={preset}
          from={from}
          to={to}
          isLoading={history.isLoading}
          isError={history.isError}
          sampleCount={history.data?.length ?? 0}
        />
      )}

      <CpuUtilizationChart
        metrics={metrics}
        historyRows={isLive ? undefined : history.data}
      />

      <div className="grid grid-cols-2 gap-3">
        <MemoryBreakdownCard metrics={metrics} />
        <SwapCard metrics={metrics} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <LoadAverageCard metrics={metrics} />
        <FileDescriptorsCard metrics={metrics} />
      </div>

      <ChartCard title="Disk Usage" height="auto">
        <DiskUsage disks={metrics.disks} />
      </ChartCard>

      <div className="grid grid-cols-2 gap-3">
        <SystemInfoCard metrics={metrics} />
        <ProcessInfoCard metrics={metrics} />
      </div>
    </div>
  );
}

function HistoryBanner({
  preset,
  from,
  to,
  isLoading,
  isError,
  sampleCount,
}: {
  preset: string;
  from: string | null;
  to: string | null;
  isLoading: boolean;
  isError: boolean;
  sampleCount: number;
}) {
  const status = isLoading
    ? 'Loading history…'
    : isError
    ? 'History fetch failed.'
    : `${sampleCount} samples`;

  return (
    <div className="px-3 py-2 rounded text-[12px] border border-border bg-card text-muted-foreground flex items-center justify-between">
      <span>
        Historical view ({preset}) — {status}
      </span>
      {from && to && (
        <span className="font-mono text-[11px]">
          {new Date(from).toLocaleTimeString()} →{' '}
          {new Date(to).toLocaleTimeString()}
        </span>
      )}
    </div>
  );
}

export default SystemPage;
