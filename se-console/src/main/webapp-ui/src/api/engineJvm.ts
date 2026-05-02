import { api } from './client';
import type {
  EngineJvmSnapshotRow,
  JvmMetrics,
  ThreadList,
} from './jvm.types';

/**
 * SE-Core (engine) JVM telemetry. Response shapes are intentionally
 * identical to the console's own JVM endpoints so the frontend can reuse
 * its existing components. See backend JvmSnapshotMapper for the reshape.
 */
export const engineJvmApi = {
  metrics(): Promise<JvmMetrics> {
    return api.get<JvmMetrics>('/api/engine/jvm/metrics');
  },
  history(from: string, to: string): Promise<EngineJvmSnapshotRow[]> {
    const qs = new URLSearchParams({ from, to }).toString();
    return api.get<EngineJvmSnapshotRow[]>(`/api/engine/jvm/history?${qs}`);
  },
  threads(): Promise<ThreadList> {
    return api.get<ThreadList>('/api/engine/jvm/threads');
  },
};
