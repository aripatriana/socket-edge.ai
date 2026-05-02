import { KpiCard } from '../../components/dashboard/KpiCard';
import { Card } from '../../components/dashboard/Card';
import { useEngineJvmMetrics } from '../../hooks/useEngineJvmMetrics';
import { useEngineJvmThreads } from '../../hooks/useEngineJvmThreads';
import { useEngineJvmHistory } from '../../hooks/useEngineJvmHistory';
import { useMetricHistory } from '../../hooks/useMetricHistory';
import { HeapChart } from '../../components/monitoring/HeapChart';
import { GcRateChart } from '../../components/monitoring/GcRateChart';
import { ThreadStates } from '../../components/monitoring/ThreadStates';
import { MemoryPools } from '../../components/monitoring/MemoryPools';
import { DeadlockBanner } from '../../components/monitoring/DeadlockBanner';
import { ThreadList } from '../../components/monitoring/ThreadList';
import { NonHeapCard } from '../../components/monitoring/NonHeapCard';
import { formatBytes, formatNumber, formatUptime } from '../../lib/format';
import {
  TimeRangeSelector,
  useTimeRange,
} from '../../components/shared/TimeRangeSelector';

/**
 * Monitoring → JVM tab — SE-Core engine JVM metrics via gRPC stream.
 *
 * Data flow:
 *   se-core gRPC SubscribeMetrics stream
 *     → GrpcMetricsSubscriber (MetricsBundle cache)
 *     → EngineJvmSnapshotService.poll() (2s)
 *     → AtomicReference cache + H2 engine_jvm_snapshot table
 *     → GET /api/engine/jvm/metrics (live) or /api/engine/jvm/history (range)
 */
export function JvmPage() {
  const { preset, setPreset, from, to, isLive } = useTimeRange('live');
  const query = useEngineJvmMetrics();
  const history = useEngineJvmHistory(from, to);
  const metrics = query.data;

  const heapHistory = useMetricHistory(metrics?.heap.usedPercent, 60, metrics?.timestamp);
  const threadHistory = useMetricHistory(metrics?.threads.liveCount, 60, metrics?.timestamp);
  const classHistory = useMetricHistory(metrics?.classes.loadedCount, 60, metrics?.timestamp);

  if (query.isLoading || !metrics) {
    return (
      <div className="flex items-center justify-center h-[200px] text-muted-foreground">
        Loading Engine JVM metrics…
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="px-4 py-3 rounded text-[13px] bg-destructive/10 text-destructive border border-destructive">
        Failed to load Engine JVM metrics. Retrying…
      </div>
    );
  }

  const totalGcCount = metrics.gc.reduce((sum, g) => sum + g.collectionCount, 0);
  const totalGcTimeMs = metrics.gc.reduce((sum, g) => sum + g.collectionTimeMs, 0);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-[14px] font-semibold text-foreground m-0">Engine JVM</h2>
        <TimeRangeSelector value={preset} onChange={setPreset} />
      </div>

      {!isLive && (
        <div className="px-3 py-2 rounded text-[12px] border border-border bg-card text-muted-foreground flex items-center justify-between">
          <span>
            Historical view ({preset}) —{' '}
            {history.isLoading
              ? 'Loading history…'
              : history.isError
              ? 'History fetch failed.'
              : `${history.data?.length ?? 0} samples`}
          </span>
          {from && to && (
            <span className="font-mono text-[11px]">
              {new Date(from).toLocaleTimeString()} →{' '}
              {new Date(to).toLocaleTimeString()}
            </span>
          )}
        </div>
      )}

      <DeadlockBanner deadlock={metrics.deadlock} />

      {/* KPI strip */}
      <div className="grid grid-cols-4 gap-3">
        <KpiCard
          label="Heap"
          value={
            metrics.heap.usedPercent != null
              ? metrics.heap.usedPercent.toFixed(1)
              : 'N/A'
          }
          unit="%"
          sub={`${formatBytes(metrics.heap.usedBytes)} / ${formatBytes(metrics.heap.maxBytes)}`}
          history={heapHistory}
          yDomain={[0, 100]}
        />
        <KpiCard
          label="Threads"
          value={formatNumber(metrics.threads.liveCount)}
          sub={`peak ${metrics.threads.peakCount} · ${metrics.threads.daemonCount} daemon`}
          history={threadHistory}
          sparkColor="hsl(var(--accent))"
        />
        <KpiCard
          label="Classes loaded"
          value={formatNumber(metrics.classes.loadedCount)}
          sub={`${formatNumber(metrics.classes.totalLoadedCount)} total · ${formatNumber(metrics.classes.unloadedCount)} unloaded`}
          history={classHistory}
        />
        <KpiCard
          label="GC (cumulative)"
          value={formatNumber(totalGcCount)}
          sub={`${totalGcTimeMs.toLocaleString()} ms total time`}
        />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-2 gap-3">
        <HeapChart
          metrics={metrics}
          historyRows={isLive ? undefined : history.data}
        />
        <GcRateChart
          collectors={metrics.gc}
          timestamp={metrics.timestamp}
          historyRows={isLive ? undefined : history.data}
        />
      </div>

      {/* Threads donut + Non-heap breakdown row */}
      <div className="grid grid-cols-2 gap-3">
        <ThreadStates threads={metrics.threads} />
        <NonHeapCard metrics={metrics} />
      </div>

      <MemoryPools pools={metrics.pools} />

      <ThreadList useThreadsHook={useEngineJvmThreads} />

      <Card className="px-4 py-3.5">
        <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
          Runtime
        </div>
        <div className="grid grid-cols-4 gap-4 text-[11px]">
          <RuntimeField label="VM" value={metrics.runtime.vmName} />
          <RuntimeField label="Vendor" value={metrics.runtime.vmVendor} />
          <RuntimeField label="Version" value={metrics.runtime.vmVersion} />
          <RuntimeField
            label="Uptime"
            value={formatUptime(Math.floor(metrics.runtime.uptimeMillis / 1000))}
          />
        </div>
        {metrics.runtime.inputArguments.length > 0 && (
          <div className="mt-3">
            <div className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground mb-1">
              JVM Arguments
            </div>
            <div className="font-mono text-[11px] text-foreground bg-muted rounded px-3 py-2 break-all leading-relaxed">
              {metrics.runtime.inputArguments.join(' ')}
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}

function RuntimeField({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1 min-w-0">
      <span className="uppercase tracking-wider font-semibold text-[10px] text-muted-foreground">
        {label}
      </span>
      <span
        className="font-mono text-[12px] text-foreground tabular-nums truncate"
        title={value}
      >
        {value || 'N/A'}
      </span>
    </div>
  );
}

export default JvmPage;
