import { useQuery } from '@tanstack/react-query';
import { engineJvmApi } from '../api/engineJvm';
import type { ThreadList } from '../api/jvm.types';

/**
 * Engine (SE-Core) thread list. Poll rate matches useJvmThreads (5s) — the
 * backend translates this to a ?details=true engine call, which is the
 * expensive path per spec-jvm-snapshot.md. Keep this hook out of pages
 * where it isn't visible.
 */
export function useEngineJvmThreads(enabled = true) {
  return useQuery<ThreadList>({
    queryKey: ['engine-jvm-threads'],
    queryFn: () => engineJvmApi.threads(),
    refetchInterval: 5_000,
    refetchIntervalInBackground: false,
    enabled,
  });
}
