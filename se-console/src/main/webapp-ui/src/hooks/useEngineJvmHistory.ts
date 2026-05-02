import { useQuery } from '@tanstack/react-query';
import { engineJvmApi } from '../api/engineJvm';
import type { EngineJvmSnapshotRow } from '../api/jvm.types';

export function useEngineJvmHistory(from: string | null, to: string | null) {
  return useQuery<EngineJvmSnapshotRow[]>({
    queryKey: ['engine-jvm-history', from, to],
    queryFn: () => engineJvmApi.history(from!, to!),
    enabled: !!from && !!to,
    staleTime: 30_000,
  });
}
