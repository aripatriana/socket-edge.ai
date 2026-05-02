import { useEffect, useMemo, useState } from 'react';
import { useNetworkMetrics } from '../../hooks/useNetworkMetrics';
import { useNetworkHistory } from '../../hooks/useNetworkHistory';
import { InterfaceSelector } from '../../components/monitoring/InterfaceSelector';
import { InterfaceBandwidthChart } from '../../components/monitoring/InterfaceBandwidthChart';
import { TcpStatesCard } from '../../components/monitoring/TcpStatesCard';
import { TcpQualityCard } from '../../components/monitoring/TcpQualityCard';
import { ListeningPortsCard } from '../../components/monitoring/ListeningPortsCard';
import { ActiveConnectionsCard } from '../../components/monitoring/ActiveConnectionsCard';
import { TcpEstablishedChart } from '../../components/monitoring/TcpEstablishedChart';
import {
  TimeRangeSelector,
  useTimeRange,
} from '../../components/shared/TimeRangeSelector';

/**
 * Monitoring → Network tab.
 *
 * Layout (top to bottom):
 *   1. Interface selector (pill group)
 *   2. Bandwidth chart (per selected interface)
 *   3. TCP States + TCP Quality — 3:2 split
 *   4. Listening Ports table
 *   5. Active Connections table
 *
 * Per Phase 5 planning: no time range selector here. Session-window only.
 *
 * Chat 3c will:
 *  - Add "Channel" column to Listening Ports table
 *  - Add per-port Accepts/sec and Rate columns once engine JMX wired
 *  - Overlay channel binding on Active Connections (optional)
 */
export function NetworkPage() {
  const { preset, setPreset, from, to, isLive } = useTimeRange('live');
  const query = useNetworkMetrics();
  const history = useNetworkHistory(from, to);
  const metrics = query.data;
  const [selectedIface, setSelectedIface] = useState<string | null>(null);

  // Pick a sensible default interface when data arrives (first up non-loopback
  // interface, fallback to any up interface, fallback to first in list).
  const interfaces = useMemo(() => metrics?.interfaces ?? [], [metrics]);
  useEffect(() => {
    if (selectedIface !== null) return;
    if (interfaces.length === 0) return;
    const preferred = interfaces.find(
      (i) => i.up && !i.name.toLowerCase().startsWith('lo')
    );
    const fallback = interfaces.find((i) => i.up) ?? interfaces[0];
    setSelectedIface((preferred ?? fallback).name);
  }, [interfaces, selectedIface]);

  if (query.isLoading || !metrics) {
    return (
      <div className="flex items-center justify-center h-[200px] text-muted-foreground">
        Loading network metrics…
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="px-4 py-3 rounded text-[13px] bg-destructive/10 text-destructive border border-destructive">
        Failed to load network metrics. Retrying…
      </div>
    );
  }

  const activeIface = interfaces.find((i) => i.name === selectedIface) ?? interfaces[0];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="text-[14px] font-semibold text-foreground m-0">
          Network
        </h2>
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

      {/* Interface selector */}
      {interfaces.length > 0 && (
        <InterfaceSelector
          interfaces={interfaces}
          selected={selectedIface}
          onSelect={setSelectedIface}
        />
      )}

      {/* Bandwidth chart */}
      {activeIface ? (
        <InterfaceBandwidthChart
          iface={activeIface}
          timestamp={metrics.timestamp}
          historyRows={isLive ? undefined : history.data}
        />
      ) : (
        <div className="px-4 py-3 rounded text-[13px] bg-muted text-muted-foreground">
          No network interfaces reported
        </div>
      )}

      {/* Historical TCP established timeline — replaces the donut in history mode */}
      {!isLive && history.data && (
        <TcpEstablishedChart rows={history.data} />
      )}

      {/* TCP states + quality row */}
      <div className="grid gap-3" style={{ gridTemplateColumns: '3fr 2fr' }}>
        <TcpStatesCard states={metrics.tcpStates} />
        <TcpQualityCard quality={metrics.tcpQuality} timestamp={metrics.timestamp} />
      </div>

      {/* Listening ports */}
      <ListeningPortsCard ports={metrics.listeningPorts} />

      {/* Active connections */}
      <ActiveConnectionsCard />
    </div>
  );
}

export default NetworkPage;
