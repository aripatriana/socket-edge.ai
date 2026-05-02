import { useQuery } from '@tanstack/react-query';
import { networkApi } from '../api/network';
import type { NetworkConnections, NetworkMetrics } from '../api/network.types';

/**
 * Summary network metrics. Polled at 2s matching other metric hooks.
 * Backend caches for 5s so each poll hits cached data most of the time.
 */
export function useNetworkMetrics() {
  return useQuery<NetworkMetrics>({
    queryKey: ['network-metrics'],
    queryFn: () => networkApi.latest(),
    refetchInterval: 2_000,
    refetchIntervalInBackground: false,
  });
}

/**
 * Full TCP connection list. Polled 5s (slower; enumeration is heavier).
 * Disabled by default — enable via `enabled` flag only when Active
 * Connections table is visible.
 */
export function useNetworkConnections(enabled = true) {
  return useQuery<NetworkConnections>({
    queryKey: ['network-connections'],
    queryFn: () => networkApi.connections(),
    refetchInterval: 5_000,
    refetchIntervalInBackground: false,
    enabled,
  });
}
