import { api } from './client';
import type {
  UsersListResponse,
  CreateUserRequest,
  CreateUserResponse,
  UserManagementEntry,
  ResetPasswordResponse,
  UsersFilter,
  UserRole,
  UserStatus,
} from './users.types';

function buildQuery(filter: UsersFilter): string {
  const sp = new URLSearchParams();
  if (filter.search) sp.set('search', filter.search);
  if (filter.role) sp.set('role', filter.role);
  if (filter.status) sp.set('status', filter.status);
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export const usersApi = {
  list(filter: UsersFilter = {}): Promise<UsersListResponse> {
    return api.get<UsersListResponse>(`/api/users${buildQuery(filter)}`);
  },

  create(request: CreateUserRequest): Promise<CreateUserResponse> {
    return api.post<CreateUserResponse>('/api/users', request);
  },

  updateRole(id: number, role: UserRole): Promise<UserManagementEntry> {
    return api.patch<UserManagementEntry>(`/api/users/${id}/role`, { role });
  },

  setStatus(id: number, status: UserStatus): Promise<UserManagementEntry> {
    // Server expects ?value=active|disabled as a query param, not JSON body —
    // it's a single-value state transition, simpler to inspect in logs.
    return api.patch<UserManagementEntry>(
      `/api/users/${id}/status?value=${encodeURIComponent(status)}`,
    );
  },

  disable(id: number): Promise<UserManagementEntry> {
    return api.delete<UserManagementEntry>(`/api/users/${id}`);
  },

  resetPassword(id: number): Promise<ResetPasswordResponse> {
    return api.post<ResetPasswordResponse>(`/api/users/${id}/reset-password`);
  },
};
