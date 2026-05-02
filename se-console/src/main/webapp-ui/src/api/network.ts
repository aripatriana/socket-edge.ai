import { api } from './client';
import type { MetricsEnvelope } from './metrics.shared.types';
import type {
  NetworkConnections,
  NetworkMetrics,
  NetworkSnapshotRow,
} from './network.types';

export const networkApi = {
  async latest(): Promise<NetworkMetrics> {
    const res = await api.get<MetricsEnvelope<NetworkMetrics>>(
      '/api/console/network/latest'
    );
    return res.metrics;
  },

  envelope(): Promise<MetricsEnvelope<NetworkMetrics>> {
    return api.get<MetricsEnvelope<NetworkMetrics>>(
      '/api/console/network/latest'
    );
  },

  history(from: string, to: string): Promise<NetworkSnapshotRow[]> {
    const qs = new URLSearchParams({ from, to }).toString();
    return api.get<NetworkSnapshotRow[]>(
      `/api/console/network/history?${qs}`
    );
  },

  /** Live connection table — still on-demand, not cached. */
  connections(): Promise<NetworkConnections> {
    return api.get<NetworkConnections>('/api/console/network/connections');
  },
};
