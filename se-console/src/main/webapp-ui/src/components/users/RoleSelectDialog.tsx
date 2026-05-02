import { useState, useEffect } from 'react';
import { X } from 'lucide-react';
import type { UserManagementEntry, UserRole } from '../../api/users.types';

interface Props {
  user: UserManagementEntry | null;
  isSelf: boolean;
  busy: boolean;
  onConfirm: (role: UserRole) => void;
  onCancel: () => void;
}

/**
 * Role change dialog. Kept separate from CreateUserDialog because the
 * flow is different — edits should be reversible and require no
 * password handoff.
 *
 * <p>Self-demotion is blocked in the UI by disabling the inline role
 * button for admin-self in the table, so this dialog generally opens
 * only for other users. If a caller slips through, the backend guard
 * still catches it.
 */
export function RoleSelectDialog({ user, isSelf, busy, onConfirm, onCancel }: Props) {
  const [role, setRole] = useState<UserRole>('operator');

  useEffect(() => {
    if (user) setRole(user.role);
  }, [user]);

  if (!user) return null;

  const unchanged = role === user.role;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onCancel}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="bg-card border border-border rounded-md shadow-lg w-[440px] max-w-[92vw]"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex items-center gap-2 px-4 py-3 border-b border-border">
          <div className="flex-1 text-[14px] font-semibold text-foreground">
            Change role for{' '}
            <span className="font-mono">{user.username}</span>
          </div>
          <button
            type="button"
            onClick={onCancel}
            className="text-muted-foreground hover:text-foreground"
            aria-label="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </header>

        <div className="px-4 py-4 space-y-3">
          <div>
            <label className="block text-[11px] uppercase tracking-wider font-semibold text-muted-foreground mb-1">
              Role
            </label>
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as UserRole)}
              disabled={busy}
              className="w-full px-3 py-1.5 text-[13px] border border-border rounded bg-background"
            >
              <option value="admin">Admin — full access</option>
              <option value="operator">Operator — edit config + control channels</option>
              <option value="viewer">Viewer — read-only</option>
            </select>
          </div>

          {isSelf && user.role === 'admin' && role !== 'admin' && (
            <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-[12px] text-red-800">
              You cannot demote yourself. Ask another admin to change your role.
            </div>
          )}
        </div>

        <footer className="flex items-center justify-end gap-2 px-4 py-3 border-t border-border bg-muted/20">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="px-3 py-1.5 text-[12px] border border-border rounded bg-card hover:bg-muted/40 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => onConfirm(role)}
            disabled={busy || unchanged || (isSelf && user.role === 'admin' && role !== 'admin')}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] border border-primary bg-primary text-primary-foreground rounded hover:bg-primary/90 disabled:opacity-50"
          >
            {busy && (
              <span className="inline-block w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin" />
            )}
            <span>{busy ? 'Saving…' : 'Save'}</span>
          </button>
        </footer>
      </div>
    </div>
  );
}
