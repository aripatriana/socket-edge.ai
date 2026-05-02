import { api } from './client';
import type { SystemMetrics } from './metrics.types';

export const metricsApi = {
  system(): Promise<SystemMetrics> {
    return api.get<SystemMetrics>('/api/system/metrics');
  },
};
