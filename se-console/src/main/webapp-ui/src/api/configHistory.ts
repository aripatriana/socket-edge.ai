import { api } from './client';
import type {
  ConfigHistoryResponse,
  ConfigVersionDetail,
  ConfigDiffResponse,
  ConfigRollbackResponse,
} from './configHistory.types';

/**
 * Config History API. Four endpoints: list, version detail, diff, rollback.
 */
export const configHistoryApi = {
  list(fileName: string): Promise<ConfigHistoryResponse> {
    return api.get<ConfigHistoryResponse>(
      `/api/config/history/${encodeURIComponent(fileName)}`
    );
  },

  getVersion(fileName: string, version: number): Promise<ConfigVersionDetail> {
    return api.get<ConfigVersionDetail>(
      `/api/config/history/${encodeURIComponent(fileName)}/${version}`
    );
  },

  diff(fileName: string, v1: number, v2: number): Promise<ConfigDiffResponse> {
    return api.get<ConfigDiffResponse>(
      `/api/config/diff/${encodeURIComponent(fileName)}/${v1}/${v2}`
    );
  },

  rollback(fileName: string, version: number): Promise<ConfigRollbackResponse> {
    return api.post<ConfigRollbackResponse>(
      `/api/config/rollback/${encodeURIComponent(fileName)}/${version}`
    );
  },
};
