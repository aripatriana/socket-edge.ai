import { api } from './client';
import type { MetricsEnvelope } from './metrics.shared.types';
import type { SystemMetrics, SystemSnapshotRow } from './metrics.types';

/**
 * Console-side system metrics — OS of the host running SE-Console.
 *
 * Two endpoints:
 *  - latest()  unwraps MetricsEnvelope<SystemMetrics> so components that
 *              only care about the DTO don't need to know about the envelope.
 *              Components that need reachability can call envelope() instead.
 *  - history() raw snapshot rows from console_system_snapshot, for charts.
 */
export const metricsApi = {
  async latest(): Promise<SystemMetrics> {
    const res = await api.get<MetricsEnvelope<SystemMetrics>>(
      '/api/console/system/latest'
    );
    return res.metrics;
  },

  envelope(): Promise<MetricsEnvelope<SystemMetrics>> {
    return api.get<MetricsEnvelope<SystemMetrics>>(
      '/api/console/system/latest'
    );
  },

  history(from: string, to: string): Promise<SystemSnapshotRow[]> {
    const qs = new URLSearchParams({ from, to }).toString();
    return api.get<SystemSnapshotRow[]>(`/api/console/system/history?${qs}`);
  },
};
