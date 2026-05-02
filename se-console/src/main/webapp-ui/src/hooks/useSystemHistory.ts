import { useQuery } from '@tanstack/react-query';
import { metricsApi } from '../api/metrics';
import type { SystemSnapshotRow } from '../api/metrics.types';

/**
 * Pulls a time-range of system snapshot rows from the backend history
 * endpoint. Keyed by [from, to] so TanStack cache correctly invalidates
 * when the user changes the range selector.
 *
 * `from` / `to` are ISO-8601 strings; null disables the query (used while
 * the range selector is still resolving).
 */
export function useSystemHistory(from: string | null, to: string | null) {
  return useQuery<SystemSnapshotRow[]>({
    queryKey: ['system-history', from, to],
    queryFn: () => metricsApi.history(from!, to!),
    enabled: !!from && !!to,
    staleTime: 30_000,
  });
}
