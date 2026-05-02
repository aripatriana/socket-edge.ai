import { api } from './client';
import type { LogTailResponse } from './logs.types';

/**
 * Logs API. Reuses the shared `api` client for Bearer token and 401 auto-clear.
 *
 * <p>The endpoint tails a file on the same server as SE-Console — no engine
 * HTTP involved. See the backend {@code LogsService} for the whitelist.
 */
export const logsApi = {
  tail(fileName: string, lines: number): Promise<LogTailResponse> {
    return api.get<LogTailResponse>(
      `/api/logs/${encodeURIComponent(fileName)}?lines=${lines}`
    );
  },
};
