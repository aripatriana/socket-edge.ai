import { useEffect } from 'react';
import { CheckCircle2, XCircle, X } from 'lucide-react';
import type { SocketActionResponse } from '../../../api/channels.types';

export interface BannerState {
  kind: 'success' | 'error';
  message: string;
  response?: SocketActionResponse;
}

interface Props {
  state: BannerState | null;
  onDismiss: () => void;
  autoHideMs?: number;
}

/**
 * Inline feedback banner shown after an action is executed.
 *
 * <p>Success banners auto-dismiss after {@code autoHideMs} (default 5s).
 * Error banners remain until the user closes them, since operators
 * usually want to read the engine's failure reason.
 */
export function ActionBanner({ state, onDismiss, autoHideMs = 5_000 }: Props) {
  useEffect(() => {
    if (!state || state.kind !== 'success') return;
    const t = setTimeout(onDismiss, autoHideMs);
    return () => clearTimeout(t);
  }, [state, autoHideMs, onDismiss]);

  if (!state) return null;

  const isOk = state.kind === 'success';
  const Icon = isOk ? CheckCircle2 : XCircle;

  const tone = isOk
    ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
    : 'bg-red-50 border-red-200 text-red-800';

  return (
    <div
      role="status"
      className={`flex items-start gap-2 rounded-md border px-3 py-2 text-[12px] ${tone}`}
    >
      <Icon className="w-4 h-4 mt-0.5 shrink-0" />
      <div className="flex-1 min-w-0">
        <div className="font-mono break-all">{state.message}</div>
        {state.response && (
          <div className="text-[11px] opacity-75 mt-0.5 font-mono">
            {state.response.action.toUpperCase()} · {state.response.scope} · {state.response.durationMs}ms
          </div>
        )}
      </div>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss"
        className="opacity-60 hover:opacity-100 transition-opacity"
      >
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}
