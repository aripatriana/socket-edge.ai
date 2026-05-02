import { useQuery } from '@tanstack/react-query';
import { metricsApi } from '../api/metrics';
import type { SystemMetrics } from '../api/metrics.types';

/**
 * Live system metrics. Polls every 2 seconds matching backend cache TTL.
 */
export function useSystemMetrics() {
  return useQuery<SystemMetrics>({
    queryKey: ['system-metrics'],
    queryFn: () => metricsApi.system(),
    refetchInterval: 2_000,
    refetchIntervalInBackground: false,
  });
}
