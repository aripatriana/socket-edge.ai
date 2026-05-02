import { useQuery } from '@tanstack/react-query';
import { channelsApi } from '../api/channels';
import type { ChannelsResponse } from '../api/channels.types';

/**
 * Channels poll. 2s cadence matches the backend's engine poll interval,
 * so the UI is always within 2s of the cache refresh.
 *
 * The backend caches the merged snapshot and does the engine call on its own
 * schedule — this hook just pulls the latest cached version.
 */
export function useChannels() {
  return useQuery<ChannelsResponse>({
    queryKey: ['channels'],
    queryFn: () => channelsApi.list(),
    refetchInterval: 2_000,
    refetchIntervalInBackground: false,
  });
}
