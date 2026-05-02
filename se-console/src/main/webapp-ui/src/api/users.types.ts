// Types for admin Users & Roles page.

export type UserRole = 'admin' | 'operator' | 'viewer';
export type UserStatus = 'active' | 'disabled';

/** Full user record as returned by the admin /api/users endpoint. */
export interface UserManagementEntry {
  id: number;
  username: string;
  role: UserRole;
  status: UserStatus;
  locked: boolean;          // derived: status=active && failedLoginCount >= threshold
  failedLoginCount: number;
  mustChangePassword: boolean;
  lastLoginAt: string | null;   // ISO-8601
  lastLoginIp: string | null;
  createdAt: string;            // ISO-8601
  createdBy: number | null;
}

export interface UsersListResponse {
  users: UserManagementEntry[];
}

export interface CreateUserRequest {
  username: string;
  role: UserRole;
}

/**
 * Response from POST /api/users. The {@code temporaryPassword} is the
 * server-generated plaintext, surfaced here ONCE — the admin must capture
 * it immediately. It is never included in any other response or in audit
 * entries.
 */
export interface CreateUserResponse {
  user: {
    id: number;
    username: string;
    role: string;
    status: string;
    mustChangePassword: boolean;
    lastLoginAt: string | null;
  };
  temporaryPassword: string;
  message: string;
}

export interface ResetPasswordResponse {
  user: UserManagementEntry;
  message: string;
}

export interface UsersFilter {
  search?: string;
  role?: UserRole | '';
  status?: UserStatus | '';
}
