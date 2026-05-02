import { useQuery } from '@tanstack/react-query';
import { channelsApi } from '../api/channels';
import type { ChannelSummary, ChannelHistoryResponse } from '../api/channels.types';

/**
 * Single-channel detail poll. 2s cadence matches the channels-list page —
 * the backend is already polling at that rate, so this just pulls the
 * freshest merged row.
 */
export function useChannelDetail(name: string | undefined) {
  return useQuery<ChannelSummary>({
    queryKey: ['channel-detail', name],
    queryFn: () => channelsApi.detail(name!),
    enabled: !!name,
    refetchInterval: 2_000,
    refetchIntervalInBackground: false,
  });
}

/**
 * History poll for chart data.
 *
 * <p>Window is driven by the UI's time-range selector. Refresh cadence is
 * scaled to the window — a 24h chart doesn't benefit from a 2s refresh
 * (the last sample barely moves the chart), and paying for the network
 * round-trip that often is wasteful.
 */
export function useChannelHistory(
  name: string | undefined,
  window: string = '4m'
) {
  return useQuery<ChannelHistoryResponse>({
    queryKey: ['channel-history', name, window],
    queryFn: () => channelsApi.history(name!, window),
    enabled: !!name,
    refetchInterval: refreshMsForWindow(window),
    refetchIntervalInBackground: false,
  });
}

/**
 * Heuristic: one refresh per ~120 visible samples feels natural.
 *  - Up to ~4 minutes → 2s refresh (Live feel).
 *  - 1 hour → 30s refresh.
 *  - 6 hour → 3 min refresh.
 *  - 24 hour → 12 min refresh.
 */
function refreshMsForWindow(window: string): number {
  const w = window.trim().toLowerCase();
  const n = parseInt(w, 10);
  if (Number.isNaN(n)) return 2_000;
  if (w.endsWith('h')) {
    if (n <= 1) return 30_000;
    if (n <= 6) return 180_000;
    return 720_000;
  }
  // seconds or minutes → live cadence
  return 2_000;
}
