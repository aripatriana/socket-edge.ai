import { useState } from 'react';
import { Plus, Search, RefreshCw, X } from 'lucide-react';
import { AppShell } from '../../components/layout/AppShell';
import { UsersTable } from '../../components/users/UsersTable';
import { CreateUserDialog } from '../../components/users/CreateUserDialog';
import { RoleSelectDialog } from '../../components/users/RoleSelectDialog';
import { ConfirmDialog } from '../../components/users/ConfirmDialog';
import {
  useUsersList,
  useUpdateUserRole,
  useSetUserStatus,
  useResetUserPassword,
} from '../../hooks/useUsers';
import { useAuthStore } from '../../stores/authStore';
import type {
  UserManagementEntry,
  UsersFilter,
  UserRole,
  UserStatus,
} from '../../api/users.types';

/**
 * Users & Roles page — admin only. AdminOnlyRoute handles the gate.
 *
 * <p>Layout: header with Create button, filter bar, table, modals for
 * create/role/disable/reset. All mutations invalidate the list cache so
 * the table refreshes without extra code here.
 */
export function UsersPage() {
  const me = useAuthStore((s) => s.user);

  const [filter, setFilter] = useState<UsersFilter>({});
  const [searchDraft, setSearchDraft] = useState('');

  const { data, isLoading, isFetching, refetch, isError, error } = useUsersList(filter);

  // Dialog state — exactly one can be open at a time. Keeping them as
  // separate nullable slots is simpler than a tagged union and reads
  // cleanly in handlers.
  const [createOpen, setCreateOpen] = useState(false);
  const [roleFor, setRoleFor] = useState<UserManagementEntry | null>(null);
  const [disableFor, setDisableFor] = useState<UserManagementEntry | null>(null);
  const [enableFor, setEnableFor] = useState<UserManagementEntry | null>(null);
  const [resetFor, setResetFor] = useState<UserManagementEntry | null>(null);
  const [banner, setBanner] = useState<{ kind: 'success' | 'error' | 'info'; message: string } | null>(null);

  const updateRoleMut = useUpdateUserRole();
  const statusMut = useSetUserStatus();
  const resetMut = useResetUserPassword();

  const applySearch = () => setFilter((f) => ({ ...f, search: searchDraft.trim() || undefined }));
  const clearFilters = () => {
    setSearchDraft('');
    setFilter({});
  };

  const hasFilters = !!filter.search || !!filter.role || !!filter.status;

  const handleRoleConfirm = async (newRole: UserRole) => {
    if (!roleFor) return;
    try {
      await updateRoleMut.mutateAsync({ id: roleFor.id, role: newRole });
      setBanner({ kind: 'success', message: `Role updated for ${roleFor.username}.` });
      setRoleFor(null);
    } catch (err) {
      setBanner({ kind: 'error', message: extractMessage(err) });
    }
  };

  const handleDisableConfirm = async () => {
    if (!disableFor) return;
    try {
      await statusMut.mutateAsync({ id: disableFor.id, status: 'disabled' });
      setBanner({ kind: 'success', message: `${disableFor.username} disabled.` });
      setDisableFor(null);
    } catch (err) {
      setBanner({ kind: 'error', message: extractMessage(err) });
    }
  };

  const handleEnableConfirm = async () => {
    if (!enableFor) return;
    try {
      await statusMut.mutateAsync({ id: enableFor.id, status: 'active' });
      setBanner({ kind: 'success', message: `${enableFor.username} enabled.` });
      setEnableFor(null);
    } catch (err) {
      setBanner({ kind: 'error', message: extractMessage(err) });
    }
  };

  const handleResetConfirm = async () => {
    if (!resetFor) return;
    try {
      await resetMut.mutateAsync({ id: resetFor.id });
      setBanner({
        kind: 'success',
        message: `${resetFor.username} will be required to change their password on next login.`,
      });
      setResetFor(null);
    } catch (err) {
      setBanner({ kind: 'error', message: extractMessage(err) });
    }
  };

  return (
    <AppShell>
      <div className="flex flex-col h-full -m-5" style={{ height: 'calc(100vh - 52px)' }}>
        <header className="flex items-center gap-3 px-4 py-3 border-b border-border bg-card">
          <div className="flex-1">
            <div className="text-[14px] font-semibold text-foreground">Users &amp; Roles</div>
            <div className="text-[11px] text-muted-foreground font-mono mt-0.5">
              Manage console accounts · admin only
            </div>
          </div>
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] border border-primary bg-primary text-primary-foreground rounded hover:bg-primary/90"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Create user</span>
          </button>
        </header>

        {banner && (
          <div
            role="status"
            className={`border-b px-4 py-2 text-[12px] flex items-start gap-2 ${
              banner.kind === 'success'
                ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                : banner.kind === 'error'
                  ? 'bg-red-50 border-red-200 text-red-800'
                  : 'bg-sky-50 border-sky-200 text-sky-800'
            }`}
          >
            <span className="flex-1 font-mono">{banner.message}</span>
            <button
              type="button"
              onClick={() => setBanner(null)}
              className="opacity-60 hover:opacity-100 text-[14px]"
              aria-label="Dismiss"
            >
              ×
            </button>
          </div>
        )}

        <div className="flex items-end gap-2 flex-wrap px-4 py-3 border-b border-border bg-card">
          <label className="flex flex-col gap-1">
            <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
              Search
            </span>
            <div className="relative">
              <Search className="absolute left-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground pointer-events-none" />
              <input
                type="text"
                value={searchDraft}
                onChange={(e) => setSearchDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') applySearch();
                }}
                placeholder="username substring"
                className="pl-7 pr-2 py-1 text-[12px] border border-border rounded bg-background w-[200px]"
              />
            </div>
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
              Role
            </span>
            <select
              value={filter.role ?? ''}
              onChange={(e) => setFilter((f) => ({ ...f, role: (e.target.value || undefined) as UserRole | undefined }))}
              className="px-2 py-1 text-[12px] border border-border rounded bg-background w-[130px]"
            >
              <option value="">All</option>
              <option value="admin">Admin</option>
              <option value="operator">Operator</option>
              <option value="viewer">Viewer</option>
            </select>
          </label>

          <label className="flex flex-col gap-1">
            <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
              Status
            </span>
            <select
              value={filter.status ?? ''}
              onChange={(e) => setFilter((f) => ({ ...f, status: (e.target.value || undefined) as UserStatus | undefined }))}
              className="px-2 py-1 text-[12px] border border-border rounded bg-background w-[130px]"
            >
              <option value="">All</option>
              <option value="active">Active</option>
              <option value="disabled">Disabled</option>
            </select>
          </label>

          <div className="flex items-center gap-1 ml-auto">
            {hasFilters && (
              <button
                type="button"
                onClick={clearFilters}
                className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] border border-border rounded bg-card text-muted-foreground hover:bg-muted/40"
              >
                <X className="w-3 h-3" />
                Clear
              </button>
            )}
            <button
              type="button"
              onClick={applySearch}
              disabled={searchDraft.trim() === (filter.search ?? '')}
              className="inline-flex items-center gap-1 px-3 py-1 text-[12px] border border-primary bg-primary text-primary-foreground rounded hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Apply
            </button>
            <button
              type="button"
              onClick={() => refetch()}
              className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] border border-border rounded bg-card text-foreground hover:bg-muted/40"
              title="Refresh"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>

        {isError && (
          <div className="m-4 rounded-md border border-destructive bg-destructive/10 p-4 text-[13px]">
            <div className="font-semibold text-destructive">Failed to load users</div>
            <div className="text-[11px] text-muted-foreground mt-1 font-mono">
              {error instanceof Error ? error.message : String(error)}
            </div>
          </div>
        )}

        <UsersTable
          users={data?.users ?? []}
          currentUserId={me?.id}
          isLoading={isLoading}
          onEditRole={setRoleFor}
          onResetPassword={setResetFor}
          onDisable={setDisableFor}
          onEnable={setEnableFor}
        />
      </div>

      {/* Dialogs */}
      <CreateUserDialog open={createOpen} onClose={() => setCreateOpen(false)} />

      <RoleSelectDialog
        user={roleFor}
        isSelf={!!roleFor && roleFor.id === me?.id}
        busy={updateRoleMut.isPending}
        onConfirm={handleRoleConfirm}
        onCancel={() => setRoleFor(null)}
      />

      <ConfirmDialog
        open={!!disableFor}
        title="Disable user?"
        body={
          disableFor && (
            <div className="space-y-2">
              <p>
                <span className="font-mono font-semibold">{disableFor.username}</span>{' '}
                will no longer be able to log in. Existing audit entries are preserved.
              </p>
              <p className="text-[11px] text-muted-foreground">
                You can re-enable the account later.
              </p>
            </div>
          )
        }
        confirmLabel="Disable"
        tone="destructive"
        busy={statusMut.isPending}
        onConfirm={handleDisableConfirm}
        onCancel={() => setDisableFor(null)}
      />

      <ConfirmDialog
        open={!!enableFor}
        title="Enable user?"
        body={
          enableFor && (
            <p>
              <span className="font-mono font-semibold">{enableFor.username}</span>{' '}
              will be able to log in again with their existing credentials.
            </p>
          )
        }
        confirmLabel="Enable"
        busy={statusMut.isPending}
        onConfirm={handleEnableConfirm}
        onCancel={() => setEnableFor(null)}
      />

      <ConfirmDialog
        open={!!resetFor}
        title="Force password change?"
        body={
          resetFor && (
            <div className="space-y-2">
              <p>
                On their next login,{' '}
                <span className="font-mono font-semibold">{resetFor.username}</span>{' '}
                will be required to choose a new password.
              </p>
              <p className="text-[11px] text-muted-foreground">
                Their current password still works for this one login.
              </p>
            </div>
          )
        }
        confirmLabel="Force change"
        busy={resetMut.isPending}
        onConfirm={handleResetConfirm}
        onCancel={() => setResetFor(null)}
      />
    </AppShell>
  );
}

// ---------------------------------------------------------------------------

function extractMessage(err: unknown): string {
  if (err instanceof Error) {
    const asAny = err as unknown as { details?: { message?: unknown } };
    const msg = asAny.details?.message;
    if (typeof msg === 'string') return msg;
    return err.message;
  }
  return String(err);
}
