import { useMemo, useState } from 'react';
import type {
  ChannelSummary,
  AggregationKey,
  EndpointRef,
  HistorySample,
} from '../../../api/channels.types';
import { useChannelHistory } from '../../../hooks/useChannelDetail';
import { formatNumber } from '../../../lib/format';
import { formatLatency, formatAgo, valueToneClass } from '../../../lib/formatEngine';
import { TimeSeriesChart, type MetricKey } from '../../../components/channels/TimeSeriesChart';
import {
  TimeRangeSelector,
  type TimeRangePreset,
} from '../../../components/shared/TimeRangeSelector';

interface Props {
  channelName: string;
  channel: ChannelSummary;
}

/**
 * Metrics tab: 6 KPI cards + 3 charts (Throughput / Pressure / Latency).
 *
 * <p>Time range selector maps preset → backend window parameter:
 * <ul>
 *   <li>{@code Live} → {@code 4m} (matches the default rolling view, 2s refresh)</li>
 *   <li>{@code 1h / 6h / 24h} → passed through with scaled refresh cadence</li>
 * </ul>
 *
 * <p>Chat 3c-1b r2 changes:
 * <ul>
 *   <li>Endpoint checkboxes are now scoped per-chart (no cross-chart leak).</li>
 *   <li>Aggregation checkboxes are multi-select — user can plot avg + max + p95
 *       simultaneously on the same chart.</li>
 *   <li>Throughput and Pressure get the full avg/min/max/p90/p95 list (engine
 *       exposes them for all three metrics).</li>
 * </ul>
 */
export function MetricsTab({ channelName, channel }: Props) {
  const [preset, setPreset] = useState<TimeRangePreset>('live');
  const window = presetToWindow(preset);
  const { data: history, isLoading, isError } = useChannelHistory(channelName, window);

  const endpoints = history?.endpoints ?? [];
  const samplesByHash = history?.samplesByHashId ?? {};

  const subtitle = preset === 'live' ? 'last 4 minutes' : `last ${preset}`;

  return (
    <div className="flex flex-col gap-4">
      {/* ============================ KPI cards ============================ */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <KpiCard
          label="CURRENT TPS"
          primary={formatNumber(channel.aggregate.throughputTps.totalAvg)}
          secondary={`peak ${formatNumber(channel.aggregate.throughputTps.maxAvg)}`}
        />
        <KpiCard
          label="LATENCY"
          primary={`avg ${formatLatency(channel.aggregate.latency.maxAvgNs)}`}
          secondary={`max ${formatLatency(channel.aggregate.latency.maxMaxNs)}`}
        />
        <KpiCard
          label="MESSAGE IN"
          primary={formatNumber(channel.aggregate.totalMsgIn)}
          secondary="lifetime"
        />
        <KpiCard
          label="MESSAGE OUT"
          primary={formatNumber(channel.aggregate.totalMsgOut)}
          secondary={
            channel.aggregate.totalMsgIn === channel.aggregate.totalMsgOut
              ? 'balanced'
              : `Δ ${formatNumber(Math.abs(channel.aggregate.totalMsgIn - channel.aggregate.totalMsgOut))}`
          }
        />
        <KpiCard
          label="IN-FLIGHT"
          primary={formatNumber(channel.aggregate.totalInFlight)}
          primaryTone={valueToneClass(channel.aggregate.totalInFlight)}
          secondary={`pressure ${formatNumber(channel.aggregate.pressureTps.totalAvg)}`}
        />
        <KpiCard
          label="ERRORS"
          primary={formatNumber(channel.aggregate.totalErrCnt)}
          primaryTone={valueToneClass(channel.aggregate.totalErrCnt, /*warn*/ true)}
          secondary={
            channel.aggregate.maxLastErrMs > 0
              ? `last ${formatAgo(channel.aggregate.maxLastErrMs)}`
              : 'no errors'
          }
        />
      </div>

      {/* Range selector for the charts below */}
      <div className="flex items-center justify-between">
        <div className="text-[12px] text-muted-foreground">
          {isLoading && 'Loading history…'}
          {isError && 'History fetch failed.'}
          {!isLoading && !isError && history && (
            <>
              {Object.values(samplesByHash).reduce((acc, arr) => acc + arr.length, 0)} samples
              across {endpoints.length} endpoint{endpoints.length === 1 ? '' : 's'}
            </>
          )}
        </div>
        <TimeRangeSelector value={preset} onChange={setPreset} />
      </div>

      {/* ============================ Charts ============================
          Each ChartPanel owns its own endpoint + aggregation state. */}

      <ChartPanel
        title="Throughput timeline"
        subtitle={`tps per endpoint · ${subtitle}`}
        metric="throughputTps"
        endpoints={endpoints}
        samplesByHash={samplesByHash}
      />

      <ChartPanel
        title="Pressure timeline"
        subtitle={`pressure tps · offered load · ${subtitle}`}
        metric="pressureTps"
        endpoints={endpoints}
        samplesByHash={samplesByHash}
      />

      <ChartPanel
        title="Latency timeline"
        subtitle={`per endpoint · ${subtitle}`}
        metric="latency"
        endpoints={endpoints}
        samplesByHash={samplesByHash}
      />
    </div>
  );
}

/**
 * Map UI preset → backend `?window=` value. Live is intentionally not "0":
 * the engine ring-buffer exposes ~4 minutes of fine-grained samples so
 * that's what Live shows with a 2s refresh cadence.
 */
function presetToWindow(preset: TimeRangePreset): string {
  switch (preset) {
    case 'live': return '4m';
    case '1h':   return '1h';
    case '6h':   return '6h';
    case '24h':  return '24h';
  }
}

// ---------------------------------------------------------------------------

/**
 * One chart's complete filter state — endpoint selection + aggregation
 * multi-select. Each mounted ChartPanel is its own React subtree, so state
 * never leaks between charts.
 */
function ChartPanel(props: {
  title: string;
  subtitle: string;
  metric: MetricKey;
  endpoints: EndpointRef[];
  samplesByHash: Record<string, HistorySample[]>;
}) {
  const { endpoints } = props;

  // Endpoint selection — default all ON via empty-set sentinel. New endpoints
  // appearing mid-session stay visible until the user explicitly toggles.
  const [selectedHashes, setSelectedHashes] = useState<Set<string>>(new Set());
  const effectiveHashes = useMemo(() => {
    if (selectedHashes.size > 0) return selectedHashes;
    return new Set(endpoints.map((e) => e.hashId));
  }, [selectedHashes, endpoints]);

  const toggleHash = (hashId: string) => {
    setSelectedHashes((prev) => {
      const base = prev.size > 0 ? prev : new Set(endpoints.map((e) => e.hashId));
      const next = new Set(base);
      if (next.has(hashId)) next.delete(hashId);
      else next.add(hashId);
      return next;
    });
  };

  // Aggregation multi-select — default just 'avg'. Guaranteed non-empty
  // (toggleAgg blocks removing the last item) so the chart always has
  // at least one series.
  const [selectedAggs, setSelectedAggs] = useState<Set<AggregationKey>>(
    () => new Set<AggregationKey>(['avg'])
  );
  const toggleAgg = (a: AggregationKey) => {
    setSelectedAggs((prev) => {
      const next = new Set(prev);
      if (next.has(a)) {
        if (next.size === 1) return prev;  // don't allow zero
        next.delete(a);
      } else {
        next.add(a);
      }
      return next;
    });
  };

  return (
    <TimeSeriesChart
      title={props.title}
      subtitle={props.subtitle}
      metric={props.metric}
      endpoints={endpoints}
      samplesByHash={props.samplesByHash}
      selectedHashes={effectiveHashes}
      onToggleEndpoint={toggleHash}
      selectedAggs={selectedAggs}
      onToggleAgg={toggleAgg}
    />
  );
}

function KpiCard({
  label,
  primary,
  secondary,
  primaryTone = 'text-foreground',
}: {
  label: string;
  primary: string;
  secondary: string;
  primaryTone?: string;
}) {
  return (
    <div className="rounded-md border border-border bg-card px-4 py-3">
      <div className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">
        {label}
      </div>
      <div className={`mt-1 text-[22px] font-semibold font-mono tabular-nums ${primaryTone}`}>
        {primary}
      </div>
      <div className="text-[11px] text-muted-foreground mt-0.5">{secondary}</div>
    </div>
  );
}
