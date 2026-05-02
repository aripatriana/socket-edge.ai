import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { usersApi } from '../api/users';
import type {
  UsersListResponse,
  CreateUserRequest,
  CreateUserResponse,
  UserManagementEntry,
  ResetPasswordResponse,
  UsersFilter,
  UserRole,
  UserStatus,
} from '../api/users.types';

/**
 * List users with optional filters. Admin-only endpoint — the caller is
 * responsible for only rendering this page to admins (AdminOnlyRoute
 * handles that at the route level).
 *
 * <p>Small table, manually refreshed after mutations via invalidation —
 * no background polling needed.
 */
export function useUsersList(filter: UsersFilter) {
  return useQuery<UsersListResponse>({
    queryKey: ['users-list', filter],
    queryFn: () => usersApi.list(filter),
    staleTime: 30_000,
    refetchOnWindowFocus: false,
  });
}

/**
 * Create user. On success the UI must show the plaintext
 * {@code temporaryPassword} from the response — it will not be
 * available again.
 */
export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation<CreateUserResponse, Error, CreateUserRequest>({
    mutationFn: (req) => usersApi.create(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users-list'] });
    },
  });
}

export function useUpdateUserRole() {
  const qc = useQueryClient();
  return useMutation<UserManagementEntry, Error, { id: number; role: UserRole }>({
    mutationFn: ({ id, role }) => usersApi.updateRole(id, role),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users-list'] });
    },
  });
}

export function useSetUserStatus() {
  const qc = useQueryClient();
  return useMutation<UserManagementEntry, Error, { id: number; status: UserStatus }>({
    mutationFn: ({ id, status }) => usersApi.setStatus(id, status),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users-list'] });
    },
  });
}

export function useResetUserPassword() {
  const qc = useQueryClient();
  return useMutation<ResetPasswordResponse, Error, { id: number }>({
    mutationFn: ({ id }) => usersApi.resetPassword(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users-list'] });
    },
  });
}
