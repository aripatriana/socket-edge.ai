import { api } from './client';
import type {
  ConfigFilesListResponse,
  ConfigFileContent,
  ValidateRequest,
  ValidateResponse,
  ApplyConfigRequest,
  ApplyConfigResponse,
  ReloadConfigResponse,
  RestartConfigResponse,
} from './config.types';

/**
 * Configuration menu API. Reuses the shared `api` client for auth.
 */
export const configApi = {
  list(): Promise<ConfigFilesListResponse> {
    return api.get<ConfigFilesListResponse>('/api/config/files');
  },

  read(name: string): Promise<ConfigFileContent> {
    return api.get<ConfigFileContent>(`/api/config/files/${encodeURIComponent(name)}`);
  },

  validate(request: ValidateRequest): Promise<ValidateResponse> {
    return api.post<ValidateResponse>('/api/config/validate', request);
  },

  apply(name: string, request: ApplyConfigRequest): Promise<ApplyConfigResponse> {
    return api.put<ApplyConfigResponse>(
      `/api/config/files/${encodeURIComponent(name)}`,
      request
    );
  },

  reload(name: string): Promise<ReloadConfigResponse> {
    return api.post<ReloadConfigResponse>(
      `/api/config/reload/${encodeURIComponent(name)}`
    );
  },

  restart(name: string): Promise<RestartConfigResponse> {
    return api.post<RestartConfigResponse>(
      `/api/config/restart/${encodeURIComponent(name)}`
    );
  },
};
