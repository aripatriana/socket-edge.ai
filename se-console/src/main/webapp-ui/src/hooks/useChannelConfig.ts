import { useQuery } from '@tanstack/react-query';
import { channelsApi } from '../api/channels';
import type { ChannelConfigResponse } from '../api/channels.types';

/**
 * Channel config poll. Slow cadence (15s) — config is semi-static on the
 * engine (reloaded via SIGHUP or /config/reload, not live). Keeping the
 * interval long avoids re-serializing the pool array and triggering
 * unnecessary re-renders of the table every couple seconds.
 */
export function useChannelConfig(name: string | undefined) {
  return useQuery<ChannelConfigResponse>({
    queryKey: ['channel-config', name],
    queryFn: () => channelsApi.config(name!),
    enabled: !!name,
    refetchInterval: 15_000,
    refetchIntervalInBackground: false,
    staleTime: 5_000,
  });
}
