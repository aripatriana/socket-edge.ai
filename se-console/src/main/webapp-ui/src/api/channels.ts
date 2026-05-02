import { api } from './client';
import type {
  ChannelsResponse,
  EngineHealthResponse,
  ChannelSummary,
  ChannelHistoryResponse,
  ChannelConfigResponse,
  SocketActionResponse,
  ActionKey,
} from './channels.types';

/**
 * Channels + engine-health API. Reuses the shared `api` client for Bearer
 * token injection and 401 auto-clear.
 */
export const channelsApi = {
  list(): Promise<ChannelsResponse> {
    return api.get<ChannelsResponse>('/api/channels');
  },
  engineHealth(): Promise<EngineHealthResponse> {
    return api.get<EngineHealthResponse>('/api/engine/health');
  },
  detail(name: string): Promise<ChannelSummary> {
    return api.get<ChannelSummary>(`/api/channels/${encodeURIComponent(name)}`);
  },
  history(name: string, windowSpec = '4m'): Promise<ChannelHistoryResponse> {
    return api.get<ChannelHistoryResponse>(
      `/api/channels/${encodeURIComponent(name)}/history?window=${encodeURIComponent(windowSpec)}`
    );
  },
  config(name: string): Promise<ChannelConfigResponse> {
    return api.get<ChannelConfigResponse>(
      `/api/channels/${encodeURIComponent(name)}/config`
    );
  },

  // --- control actions (Chat 3c-1d) ----------------------------------------

  /** Channel-level action — affects all sockets in the channel. */
  channelAction(name: string, action: ActionKey): Promise<SocketActionResponse> {
    return api.post<SocketActionResponse>(
      `/api/channels/${encodeURIComponent(name)}/${action}`
    );
  },

  /** Single-socket action — scoped to one hashId. */
  socketAction(name: string, hashId: string, action: ActionKey): Promise<SocketActionResponse> {
    return api.post<SocketActionResponse>(
      `/api/channels/${encodeURIComponent(name)}/sockets/${encodeURIComponent(hashId)}/${action}`
    );
  },
};
