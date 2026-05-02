import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { configHistoryApi } from '../api/configHistory';
import type {
  ConfigDiffResponse,
  ConfigHistoryResponse,
  ConfigRollbackResponse,
  ConfigVersionDetail,
} from '../api/configHistory.types';

/**
 * History list for a file. Moderate stale-time — operators rarely look at
 * history while someone else is applying changes, but we want the list to
 * refresh after a rollback.
 */
export function useConfigHistory(fileName: string | null) {
  return useQuery<ConfigHistoryResponse>({
    queryKey: ['config-history', fileName],
    queryFn: () => configHistoryApi.list(fileName!),
    enabled: !!fileName,
    staleTime: 30_000,
  });
}

export function useConfigVersion(fileName: string | null, version: number | null) {
  return useQuery<ConfigVersionDetail>({
    queryKey: ['config-version', fileName, version],
    queryFn: () => configHistoryApi.getVersion(fileName!, version!),
    enabled: !!fileName && version !== null,
    staleTime: Infinity,   // version content is immutable — safe to cache forever
  });
}

export function useConfigDiff(
  fileName: string | null,
  v1: number | null,
  v2: number | null,
) {
  return useQuery<ConfigDiffResponse>({
    queryKey: ['config-diff', fileName, v1, v2],
    queryFn: () => configHistoryApi.diff(fileName!, v1!, v2!),
    enabled: !!fileName && v1 !== null && v2 !== null && v1 !== v2,
    staleTime: Infinity,   // diff between two immutable versions is also immutable
  });
}

export function useConfigRollback() {
  const qc = useQueryClient();
  return useMutation<
    ConfigRollbackResponse,
    Error,
    { fileName: string; version: number }
  >({
    mutationFn: ({ fileName, version }) => configHistoryApi.rollback(fileName, version),
    onSuccess: (_data, variables) => {
      // History list + editor's cached file content all need to refresh.
      qc.invalidateQueries({ queryKey: ['config-history', variables.fileName] });
      qc.invalidateQueries({ queryKey: ['config-files'] });
      qc.invalidateQueries({ queryKey: ['config-file', variables.fileName] });
      // channel.conf rollback also affects the channels view.
      if (variables.fileName === 'channel.conf') {
        qc.invalidateQueries({ queryKey: ['channels'] });
        qc.invalidateQueries({ queryKey: ['channel-detail'] });
        qc.invalidateQueries({ queryKey: ['channel-config'] });
      }
    },
  });
}
