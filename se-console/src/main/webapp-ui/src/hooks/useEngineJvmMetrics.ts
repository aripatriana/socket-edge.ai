import { useQuery } from '@tanstack/react-query';
import { engineJvmApi } from '../api/engineJvm';
import type { JvmMetrics } from '../api/jvm.types';

/**
 * Engine (SE-Core) JVM metrics — 2s poll.
 *
 * Note: unlike useJvmMetrics (which triggers the console to sample its OWN
 * JVM on each request), this hook polls the console's cache, which is
 * populated by the scheduled EngineJvmSnapshotService backend poller. So
 * the actual engine HTTP load is independent of how many browser tabs are
 * open — this is a cache read on the console's hot path.
 */
export function useEngineJvmMetrics() {
  return useQuery<JvmMetrics>({
    queryKey: ['engine-jvm-metrics'],
    queryFn: () => engineJvmApi.metrics(),
    refetchInterval: 2_000,
    refetchIntervalInBackground: false,
  });
}
