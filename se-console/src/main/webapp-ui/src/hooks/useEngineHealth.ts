import { useQuery } from '@tanstack/react-query';
import { channelsApi } from '../api/channels';
import type { EngineHealthResponse } from '../api/channels.types';

/**
 * Engine health poll — drives the topbar badge. 5s cadence matches the
 * backend's /healthcheck poll. Runs in background too so the badge reflects
 * current engine state even while the user is on a non-Channels page.
 */
export function useEngineHealth() {
  return useQuery<EngineHealthResponse>({
    queryKey: ['engine-health'],
    queryFn: () => channelsApi.engineHealth(),
    refetchInterval: 5_000,
    refetchIntervalInBackground: true,
    retry: false,
  });
}
