import type { ReactNode } from 'react';

interface Props {
  open: boolean;
  title: string;
  body: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'default' | 'destructive';
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Minimal confirm modal used by the Users page. No portal — a fixed
 * overlay is enough for a small admin app, and avoiding a portal keeps
 * the z-index story simple (page → overlay → modal, all within the
 * normal DOM order).
 *
 * <p>{@code onCancel} fires on backdrop click AND on the Cancel button,
 * so parent state can reset uniformly.
 */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'default',
  busy,
  onConfirm,
  onCancel,
}: Props) {
  if (!open) return null;

  const confirmClass =
    tone === 'destructive'
      ? 'border-red-300 bg-red-600 text-white hover:bg-red-700'
      : 'border-primary bg-primary text-primary-foreground hover:bg-primary/90';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onCancel}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="bg-card border border-border rounded-md shadow-lg w-[440px] max-w-[92vw] flex flex-col overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="px-4 py-3 border-b border-border">
          <div className="text-[14px] font-semibold text-foreground">{title}</div>
        </header>
        <div className="px-4 py-4 text-[13px] text-foreground">{body}</div>
        <footer className="flex items-center justify-end gap-2 px-4 py-3 border-t border-border bg-muted/20">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="px-3 py-1.5 text-[12px] border border-border rounded bg-card hover:bg-muted/40 disabled:opacity-50"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] border rounded disabled:opacity-50 ${confirmClass}`}
          >
            {busy && (
              <span className="inline-block w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin" />
            )}
            <span>{confirmLabel}</span>
          </button>
        </footer>
      </div>
    </div>
  );
}
