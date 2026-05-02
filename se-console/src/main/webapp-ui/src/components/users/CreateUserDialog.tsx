import { useState } from 'react';
import { Copy, Check, X } from 'lucide-react';
import { useCreateUser } from '../../hooks/useUsers';
import type { UserRole, CreateUserResponse } from '../../api/users.types';

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * Two-phase dialog:
 *
 * <ol>
 *   <li><b>Form phase</b> — collect username + role, submit.</li>
 *   <li><b>Reveal phase</b> — after success, show the plaintext temporary
 *       password. The admin must copy it before closing; there is no
 *       way to retrieve it again.</li>
 * </ol>
 *
 * <p>The reveal is intentionally a full-dialog takeover rather than a
 * small toast. A toast can be dismissed by mis-click; this is the one
 * place in the system where losing focus = losing data.
 */
export function CreateUserDialog({ open, onClose }: Props) {
  const [username, setUsername] = useState('');
  const [role, setRole] = useState<UserRole>('operator');
  const [result, setResult] = useState<CreateUserResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const createMut = useCreateUser();

  if (!open) return null;

  const reset = () => {
    setUsername('');
    setRole('operator');
    setResult(null);
    setFormError(null);
    createMut.reset();
  };

  const close = () => {
    reset();
    onClose();
  };

  const submit = async () => {
    setFormError(null);
    const trimmed = username.trim();
    if (!trimmed) {
      setFormError('Username is required.');
      return;
    }
    try {
      const res = await createMut.mutateAsync({ username: trimmed, role });
      setResult(res);
    } catch (err) {
      setFormError(extractMessage(err));
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={close}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="bg-card border border-border rounded-md shadow-lg w-[480px] max-w-[92vw]"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex items-center gap-2 px-4 py-3 border-b border-border">
          <div className="flex-1 text-[14px] font-semibold text-foreground">
            {result ? 'User created' : 'Create new user'}
          </div>
          <button
            type="button"
            onClick={close}
            className="text-muted-foreground hover:text-foreground"
            aria-label="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </header>

        {result ? (
          <RevealPhase response={result} onDone={close} />
        ) : (
          <FormPhase
            username={username}
            setUsername={setUsername}
            role={role}
            setRole={setRole}
            formError={formError}
            isSubmitting={createMut.isPending}
            onSubmit={submit}
            onCancel={close}
          />
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function FormPhase({
  username,
  setUsername,
  role,
  setRole,
  formError,
  isSubmitting,
  onSubmit,
  onCancel,
}: {
  username: string;
  setUsername: (v: string) => void;
  role: UserRole;
  setRole: (v: UserRole) => void;
  formError: string | null;
  isSubmitting: boolean;
  onSubmit: () => void;
  onCancel: () => void;
}) {
  return (
    <>
      <div className="px-4 py-4 space-y-4">
        <div>
          <label className="block text-[11px] uppercase tracking-wider font-semibold text-muted-foreground mb-1">
            Username
          </label>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !isSubmitting) onSubmit();
            }}
            disabled={isSubmitting}
            autoFocus
            placeholder="e.g. jane.doe"
            className="w-full px-3 py-1.5 text-[13px] border border-border rounded bg-background font-mono"
          />
          <p className="text-[10px] text-muted-foreground mt-1">
            Letters, digits, dot, underscore, hyphen. 3–64 characters.
          </p>
        </div>

        <div>
          <label className="block text-[11px] uppercase tracking-wider font-semibold text-muted-foreground mb-1">
            Role
          </label>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as UserRole)}
            disabled={isSubmitting}
            className="w-full px-3 py-1.5 text-[13px] border border-border rounded bg-background"
          >
            <option value="admin">Admin — full access including user management</option>
            <option value="operator">Operator — can edit config and control channels</option>
            <option value="viewer">Viewer — read-only access</option>
          </select>
        </div>

        <div className="rounded border border-border bg-muted/30 p-3 text-[11px] text-muted-foreground">
          A temporary password will be generated and shown once on the next screen.
          The user will be required to change it on first login.
        </div>

        {formError && (
          <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-[12px] text-red-800">
            {formError}
          </div>
        )}
      </div>

      <footer className="flex items-center justify-end gap-2 px-4 py-3 border-t border-border bg-muted/20">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
          className="px-3 py-1.5 text-[12px] border border-border rounded bg-card hover:bg-muted/40 disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={onSubmit}
          disabled={isSubmitting || !username.trim()}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] border border-primary bg-primary text-primary-foreground rounded hover:bg-primary/90 disabled:opacity-50"
        >
          {isSubmitting && (
            <span className="inline-block w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin" />
          )}
          <span>{isSubmitting ? 'Creating…' : 'Create user'}</span>
        </button>
      </footer>
    </>
  );
}

// ---------------------------------------------------------------------------

function RevealPhase({
  response,
  onDone,
}: {
  response: CreateUserResponse;
  onDone: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(response.temporaryPassword);
      setCopied(true);
      setTimeout(() => setCopied(false), 2_000);
    } catch {
      // Clipboard may be blocked (insecure origin, user permissions).
      // Surface the failure silently — the password is still visible
      // on screen for manual copy.
      setCopied(false);
    }
  };

  return (
    <>
      <div className="px-4 py-4 space-y-4">
        <div className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-[12px] text-amber-900">
          <b>This password will not be shown again.</b> Copy it now and deliver it
          to the user through a secure channel.
        </div>

        <div>
          <div className="text-[11px] uppercase tracking-wider font-semibold text-muted-foreground mb-1">
            Username
          </div>
          <div className="font-mono text-[14px] text-foreground">
            {response.user.username}
          </div>
        </div>

        <div>
          <div className="text-[11px] uppercase tracking-wider font-semibold text-muted-foreground mb-1">
            Temporary password
          </div>
          <div className="flex items-center gap-2">
            <code className="flex-1 font-mono text-[14px] bg-background border border-border rounded px-3 py-2 select-all">
              {response.temporaryPassword}
            </code>
            <button
              type="button"
              onClick={copy}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-[12px] border border-border rounded bg-card hover:bg-muted/40"
            >
              {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
          </div>
        </div>

        <div className="text-[11px] text-muted-foreground">
          {response.message}
        </div>
      </div>

      <footer className="flex items-center justify-end gap-2 px-4 py-3 border-t border-border bg-muted/20">
        <button
          type="button"
          onClick={onDone}
          className="px-3 py-1.5 text-[12px] border border-primary bg-primary text-primary-foreground rounded hover:bg-primary/90"
        >
          Done
        </button>
      </footer>
    </>
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
