import { api } from './client';

export interface UserInfo {
  id: number;
  username: string;
  role: 'admin' | 'operator' | 'viewer';
  status: 'active' | 'disabled';
  mustChangePassword: boolean;
  lastLoginAt: string | null;
}

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  user: UserInfo;
  mustChangePassword: boolean;
}

export const authApi = {
  login(username: string, password: string): Promise<LoginResult> {
    return api.post<LoginResult>('/api/auth/login', { username, password }, { skipAuth: true });
  },

  changePassword(username: string, currentPassword: string, newPassword: string): Promise<void> {
    // The backend accepts this endpoint with or without a Bearer token. We send
    // the token when we have one so the action is logged against the right user;
    // the username in the body is a fallback for unauthenticated callers.
    return api.post<void>('/api/auth/change-password', { username, currentPassword, newPassword });
  },

  logout(): Promise<void> {
    return api.post<void>('/api/auth/logout');
  },

  me(): Promise<UserInfo> {
    return api.get<UserInfo>('/api/auth/me');
  },
};
