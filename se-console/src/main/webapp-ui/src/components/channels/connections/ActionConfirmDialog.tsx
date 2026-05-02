import { useEffect } from 'react';
import { AlertTriangle, Play, Square, RotateCw, X } from 'lucide-react';
import type { ActionKey, ActionScope } from '../../../api/channels.types';

export interface ConfirmRequest {
  action: ActionKey;
  scope: ActionScope;
  targetLabel: string;          // e.g. "fello" or "fello-client-.166:26000"
  affectedCount: number;        // 1 for per-socket, socketsTotal for channel-level
  currentStatus: string;        // e.g. "ACTIVE", "DEGRADED · 2/4 up"
  consequence?: string;         // optional descriptor, e.g. "5 in-flight messages will be rejected"
}

interface Props {
  request: ConfirmRequest | null;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * Custom confirmation modal for destructive control actions.
 * Renders null when {@code request} is null — caller controls visibility.
 *
 * <p>Keyboard: Escape cancels, Enter confirms (when not busy).
 */
export function ActionConfirmDialog({ request, busy, onCancel, onConfirm }: Props) {
  useEffect(() => {
    if (!request) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel();
      else if (e.key === 'Enter' && !busy) onConfirm();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [request, busy, onCancel, onConfirm]);

  if (!request) return null;

  const isDestructive = request.action === 'stop' || request.action === 'restart';
  const headerTone = isDestructive ? 'text-destructive' : 'text-foreground';
  const actionMeta = ACTION_META[request.action];

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="action-confirm-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      {/* backdrop */}
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-[1px]"
        onClick={busy ? undefined : onCancel}
      />

      <div className="relative w-full max-w-md rounded-lg border border-border bg-card shadow-xl">
        {/* Header */}
        <header className="flex items-start gap-3 p-4 border-b border-border">
          <div className={`mt-0.5 ${headerTone}`}>
            {isDestructive ? (
              <AlertTriangle className="w-5 h-5" />
            ) : (
              <actionMeta.icon className="w-5 h-5" />
            )}
          </div>
          <div className="flex-1">
            <h2 id="action-confirm-title" className={`text-[14px] font-semibold ${headerTone}`}>
              {actionMeta.title}
            </h2>
            <p className="text-[11px] text-muted-foreground mt-0.5 font-mono">
              {request.scope === 'CHANNEL' ? 'Channel-level action' : 'Socket-level action'}
            </p>
          </div>
          <button
            type="button"
            onClick={busy ? undefined : onCancel}
            disabled={busy}
            className="text-muted-foreground hover:text-foreground transition-colors disabled:opacity-40"
            aria-label="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </header>

        {/* Body */}
        <div className="p-4 space-y-3 text-[13px]">
          <p className="text-foreground">{actionMeta.sentence(request.targetLabel)}</p>

          <dl className="rounded-md bg-muted/40 border border-border p-3 grid grid-cols-[110px_1fr] gap-x-3 gap-y-1.5 text-[12px] font-mono">
            <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
              target
            </dt>
            <dd className="text-foreground truncate">{request.targetLabel}</dd>

            <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
              current
            </dt>
            <dd className="text-foreground">{request.currentStatus}</dd>

            <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
              affects
            </dt>
            <dd className="text-foreground">
              {request.affectedCount} socket{request.affectedCount === 1 ? '' : 's'}
            </dd>

            {request.consequence && (
              <>
                <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
                  impact
                </dt>
                <dd className="text-amber-700">{request.consequence}</dd>
              </>
            )}
          </dl>
        </div>

        {/* Footer */}
        <footer className="flex items-center justify-end gap-2 p-3 border-t border-border bg-muted/20">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="px-3 py-1.5 text-[12px] rounded border border-border bg-card hover:bg-muted/40 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className={`px-3 py-1.5 text-[12px] rounded text-white transition-colors disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-1.5 ${
              isDestructive
                ? 'bg-destructive hover:bg-destructive/90'
                : 'bg-primary hover:bg-primary/90'
            }`}
          >
            {busy && (
              <span className="inline-block w-3.5 h-3.5 border-2 border-white/70 border-t-transparent rounded-full animate-spin" />
            )}
            <span>Confirm {actionMeta.verb}</span>
          </button>
        </footer>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

const ACTION_META: Record<ActionKey, {
  title: string;
  verb: string;
  icon: React.ComponentType<{ className?: string }>;
  sentence: (target: string) => string;
}> = {
  start: {
    title: 'Start socket',
    verb: 'start',
    icon: Play,
    sentence: (t) => `Are you sure you want to start ${t}? This will open the configured listen port / outbound connections.`,
  },
  stop: {
    title: 'Stop socket',
    verb: 'stop',
    icon: Square,
    sentence: (t) => `Are you sure you want to stop ${t}? Existing connections will be closed gracefully.`,
  },
  restart: {
    title: 'Restart socket',
    verb: 'restart',
    icon: RotateCw,
    sentence: (t) => `Are you sure you want to restart ${t}? The socket will be stopped and then started again — expect a brief interruption.`,
  },
};
