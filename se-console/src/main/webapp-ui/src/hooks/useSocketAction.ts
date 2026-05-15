import { useMutation, useQueryClient } from '@tanstack/react-query';
import { channelsApi } from '../api/channels';
import type { ActionKey, SocketActionResponse } from '../api/channels.types';

/**
 * Mutation hooks for control actions. After each successful call we
 * invalidate the channel detail + list caches so the UI reflects the
 * new state without waiting for the next 2s poll tick.
 *
 * <p>Note: the 502 response from SE-Console (engine rejected) still
 * comes back as a non-2xx status, which TanStack treats as an error —
 * so {@code onError} fires with the parsed envelope in the error body.
 */
export function useChannelAction(channelName: string) {
  const qc = useQueryClient();
  return useMutation<SocketActionResponse, Error, ActionKey>({
    mutationFn: (action) => channelsApi.channelAction(channelName, action),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['channel-detail', channelName] });
      qc.invalidateQueries({ queryKey: ['channels'] });
    },
  });
}

export function useSocketAction(channelName: string) {
  const qc = useQueryClient();
  return useMutation<
    SocketActionResponse,
    Error,
    { bindingId: string; action: ActionKey }
  >({
    mutationFn: ({ bindingId, action }) =>
      channelsApi.socketAction(channelName, bindingId, action),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['channel-detail', channelName] });
      qc.invalidateQueries({ queryKey: ['channels'] });
    },
  });
}
