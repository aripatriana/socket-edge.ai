import { useState } from 'react';
import { Key, Ban, Play, MoreVertical } from 'lucide-react';
import type { UserManagementEntry, UserRole } from '../../api/users.types';

interface Props {
  users: UserManagementEntry[];
  currentUserId: number | undefined;  // own account, for self-action guards
  isLoading: boolean;
  onEditRole: (user: UserManagementEntry) => void;
  onResetPassword: (user: UserManagementEntry) => void;
  onDisable: (user: UserManagementEntry) => void;
  onEnable: (user: UserManagementEntry) => void;
}

/**
 * Users table. One row per account. Role is edited via an inline
 * dropdown — it's the most common change and deserves minimal
 * friction. Other actions live in a row-level action menu to keep the
 * row compact.
 *
 * <p>Self-action guards are applied both here (disable actions on the
 * logged-in user's own row) and in the backend. UI guards prevent the
 * obvious footguns; backend guards prevent end-runs via direct API.
 */
export function UsersTable({
  users,
  currentUserId,
  isLoading,
  onEditRole,
  onResetPassword,
  onDisable,
  onEnable,
}: Props) {
  if (isLoading && users.length === 0) {
    return (
      <div className="flex-1 grid place-items-center text-[12px] text-muted-foreground">
        Loading users…
      </div>
    );
  }
  if (!isLoading && users.length === 0) {
    return (
      <div className="flex-1 grid place-items-center p-6">
        <div className="rounded-md border border-dashed border-border bg-muted/20 px-8 py-10 text-center text-[13px] text-muted-foreground max-w-md">
          <div className="font-semibold text-foreground mb-2">No users match</div>
          <div className="text-[11px] font-mono">Try clearing filters.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-auto">
      <table className="w-full text-[12px]">
        <thead className="sticky top-0 bg-card border-b border-border z-10">
          <tr className="text-[10px] uppercase tracking-wider text-muted-foreground">
            <th className="text-left px-3 py-2 font-semibold">Username</th>
            <th className="text-left px-3 py-2 font-semibold">Role</th>
            <th className="text-left px-3 py-2 font-semibold">Status</th>
            <th className="text-left px-3 py-2 font-semibold">Last Login</th>
            <th className="text-left px-3 py-2 font-semibold">Created</th>
            <th className="text-right px-3 py-2 font-semibold w-20">Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <Row
              key={u.id}
              user={u}
              isSelf={currentUserId === u.id}
              onEditRole={onEditRole}
              onResetPassword={onResetPassword}
              onDisable={onDisable}
              onEnable={onEnable}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Row({
  user,
  isSelf,
  onEditRole,
  onResetPassword,
  onDisable,
  onEnable,
}: {
  user: UserManagementEntry;
  isSelf: boolean;
  onEditRole: (u: UserManagementEntry) => void;
  onResetPassword: (u: UserManagementEntry) => void;
  onDisable: (u: UserManagementEntry) => void;
  onEnable: (u: UserManagementEntry) => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <tr className="border-b border-border hover:bg-muted/20">
      <td className="px-3 py-2 font-mono">
        <div className="flex items-center gap-2">
          <span>{user.username}</span>
          {isSelf && (
            <span className="inline-flex px-1.5 py-[1px] rounded border border-sky-200 bg-sky-50 text-sky-700 text-[9px] font-semibold tracking-wide">
              YOU
            </span>
          )}
          {user.mustChangePassword && (
            <span
              className="inline-flex px-1.5 py-[1px] rounded border border-amber-200 bg-amber-50 text-amber-800 text-[9px] font-semibold tracking-wide"
              title="User must change password on next login"
            >
              MUST CHANGE PW
            </span>
          )}
        </div>
      </td>

      <td className="px-3 py-2">
        <button
          type="button"
          onClick={() => onEditRole(user)}
          disabled={isSelf && user.role === 'admin'}
          className="inline-flex items-center gap-1 text-[12px] hover:underline disabled:no-underline disabled:cursor-not-allowed"
          title={isSelf && user.role === 'admin' ? "Can't demote yourself" : 'Change role'}
        >
          <RoleBadge role={user.role} />
        </button>
      </td>

      <td className="px-3 py-2">
        <StatusBadge status={user.status} locked={user.locked} />
      </td>

      <td className="px-3 py-2 font-mono text-[11px] text-muted-foreground">
        {user.lastLoginAt ? (
          <>
            {formatTime(user.lastLoginAt)}
            {user.lastLoginIp && (
              <span className="ml-1 text-[10px]">from {user.lastLoginIp}</span>
            )}
          </>
        ) : (
          <span className="italic">never</span>
        )}
      </td>

      <td className="px-3 py-2 font-mono text-[11px] text-muted-foreground">
        {formatTime(user.createdAt)}
      </td>

      <td className="px-3 py-2 text-right relative">
        <button
          type="button"
          onClick={() => setMenuOpen((v) => !v)}
          className="inline-flex items-center justify-center w-7 h-7 rounded hover:bg-muted/50"
          aria-label="Row actions"
          aria-expanded={menuOpen}
        >
          <MoreVertical className="w-4 h-4 text-muted-foreground" />
        </button>

        {menuOpen && (
          <>
            <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-2 top-9 z-20 bg-card border border-border rounded-md shadow-lg w-56 py-1">
              <MenuItem
                icon={<Key className="w-3.5 h-3.5" />}
                label="Reset password"
                detail="Force change on next login"
                disabled={isSelf}
                onClick={() => { setMenuOpen(false); onResetPassword(user); }}
              />
              {user.status === 'active' ? (
                <MenuItem
                  icon={<Ban className="w-3.5 h-3.5 text-red-600" />}
                  label="Disable user"
                  detail="Keeps audit trail intact"
                  disabled={isSelf}
                  tone="destructive"
                  onClick={() => { setMenuOpen(false); onDisable(user); }}
                />
              ) : (
                <MenuItem
                  icon={<Play className="w-3.5 h-3.5 text-emerald-600" />}
                  label="Enable user"
                  detail="Re-enable access"
                  onClick={() => { setMenuOpen(false); onEnable(user); }}
                />
              )}
            </div>
          </>
        )}
      </td>
    </tr>
  );
}

function MenuItem({
  icon,
  label,
  detail,
  disabled,
  tone,
  onClick,
}: {
  icon: React.ReactNode;
  label: string;
  detail?: string;
  disabled?: boolean;
  tone?: 'destructive';
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={[
        'w-full flex items-start gap-2 px-3 py-1.5 text-left text-[12px] transition-colors',
        disabled
          ? 'opacity-40 cursor-not-allowed'
          : tone === 'destructive'
            ? 'hover:bg-red-50 text-red-700'
            : 'hover:bg-muted/40 text-foreground',
      ].join(' ')}
    >
      <span className="mt-0.5">{icon}</span>
      <span className="flex-1">
        <span className="block font-semibold">{label}</span>
        {detail && (
          <span className="block text-[10px] text-muted-foreground font-normal">
            {detail}
          </span>
        )}
      </span>
    </button>
  );
}

function RoleBadge({ role }: { role: UserRole }) {
  const cls =
    role === 'admin'
      ? 'bg-red-50 text-red-800 border-red-200'
      : role === 'operator'
        ? 'bg-sky-50 text-sky-800 border-sky-200'
        : 'bg-muted text-foreground border-border';
  return (
    <span className={`inline-flex px-1.5 py-[1px] rounded border text-[10px] font-semibold tracking-wide uppercase ${cls}`}>
      {role}
    </span>
  );
}

function StatusBadge({ status, locked }: { status: string; locked: boolean }) {
  if (locked) {
    return (
      <span className="inline-flex px-1.5 py-[1px] rounded border border-amber-200 bg-amber-50 text-amber-800 text-[10px] font-semibold tracking-wide">
        LOCKED
      </span>
    );
  }
  if (status === 'active') {
    return (
      <span className="inline-flex px-1.5 py-[1px] rounded border border-emerald-200 bg-emerald-50 text-emerald-800 text-[10px] font-semibold tracking-wide">
        ACTIVE
      </span>
    );
  }
  return (
    <span className="inline-flex px-1.5 py-[1px] rounded border border-border bg-muted text-muted-foreground text-[10px] font-semibold tracking-wide">
      DISABLED
    </span>
  );
}

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  const y = d.getFullYear();
  const M = String(d.getMonth() + 1).padStart(2, '0');
  const D = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${M}-${D} ${h}:${m}`;
}
