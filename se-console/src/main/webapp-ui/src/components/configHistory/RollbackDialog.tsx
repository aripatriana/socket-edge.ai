import { useEffect } from 'react';
import { AlertTriangle, RotateCcw, X } from 'lucide-react';
import type { ConfigVersionSummary } from '../../api/configHistory.types';

interface Props {
  /** null when dialog is hidden */
  targetVersion: ConfigVersionSummary | null;
  currentVersion: number | null;
  fileName: string;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * Confirmation modal for a rollback. Emphasises that this is an
 * append-only operation — nothing is deleted; a new version is
 * created whose content matches the chosen target.
 *
 * <p>Keyboard: Escape cancels, Enter confirms (when not busy).
 */
export function RollbackDialog({
  targetVersion,
  currentVersion,
  fileName,
  busy,
  onCancel,
  onConfirm,
}: Props) {
  useEffect(() => {
    if (!targetVersion) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel();
      else if (e.key === 'Enter' && !busy) onConfirm();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [targetVersion, busy, onCancel, onConfirm]);

  if (!targetVersion) return null;

  const expectedNewVersion = currentVersion === null ? '?' : String(currentVersion + 1);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="rollback-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-[1px]"
        onClick={busy ? undefined : onCancel}
      />

      <div className="relative w-full max-w-md rounded-lg border border-border bg-card shadow-xl">
        <header className="flex items-start gap-3 p-4 border-b border-border">
          <div className="mt-0.5 text-amber-600">
            <AlertTriangle className="w-5 h-5" />
          </div>
          <div className="flex-1">
            <h2 id="rollback-title" className="text-[14px] font-semibold text-foreground">
              Roll back configuration
            </h2>
            <p className="text-[11px] text-muted-foreground mt-0.5 font-mono">
              {fileName}
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

        <div className="p-4 space-y-3 text-[13px]">
          <p className="text-foreground">
            This will create a <span className="font-mono font-semibold">v{expectedNewVersion}</span> whose
            content matches{' '}
            <span className="font-mono font-semibold">v{targetVersion.version}</span>. The current version
            stays in history; nothing is deleted.
          </p>

          <dl className="rounded-md bg-muted/40 border border-border p-3 grid grid-cols-[110px_1fr] gap-x-3 gap-y-1.5 text-[12px] font-mono">
            <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
              target
            </dt>
            <dd className="text-foreground">v{targetVersion.version}</dd>

            <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
              current
            </dt>
            <dd className="text-foreground">
              {currentVersion === null ? '—' : `v${currentVersion}`}
            </dd>

            <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
              becomes
            </dt>
            <dd className="text-emerald-700 font-semibold">v{expectedNewVersion}</dd>

            {targetVersion.description && (
              <>
                <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
                  note
                </dt>
                <dd className="text-foreground truncate">{targetVersion.description}</dd>
              </>
            )}
          </dl>

          <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-[11px] text-amber-800">
            The engine will reload with the restored content. Expect a brief pause while
            connections re-establish.
          </div>
        </div>

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
            className="px-3 py-1.5 text-[12px] rounded text-white bg-amber-600 hover:bg-amber-700 disabled:opacity-60 disabled:cursor-not-allowed inline-flex items-center gap-1.5 transition-colors"
          >
            {busy ? (
              <span className="inline-block w-3.5 h-3.5 border-2 border-white/70 border-t-transparent rounded-full animate-spin" />
            ) : (
              <RotateCcw className="w-3.5 h-3.5" />
            )}
            <span>Confirm rollback</span>
          </button>
        </footer>
      </div>
    </div>
  );
}
