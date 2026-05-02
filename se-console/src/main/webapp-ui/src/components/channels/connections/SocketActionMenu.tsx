import { useEffect, useRef, useState } from 'react';
import { MoreVertical, Play, Square, RotateCw } from 'lucide-react';
import type { ActionKey, SocketStatus } from '../../../api/channels.types';

interface Props {
  status: SocketStatus;
  disabled?: boolean;
  onAction: (action: ActionKey) => void;
}

/**
 * Three-dot dropdown for per-row socket actions. Options are enabled
 * based on the socket's current status:
 *
 * <pre>
 *   ACTIVE / LISTEN / STANDBY / WAIT → Stop + Restart
 *   DOWN / ERROR                    → Start + Restart
 * </pre>
 *
 * Closes on outside click, Escape, or after an action is chosen.
 */
export function SocketActionMenu({ status, disabled, onAction }: Props) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const up = status === 'ACTIVE' || status === 'LISTEN' || status === 'STANDBY' || status === 'WAIT';

  const items: Array<{ key: ActionKey; label: string; icon: React.ComponentType<{ className?: string }>; enabled: boolean }> = [
    { key: 'start',   label: 'Start',   icon: Play,     enabled: !up },
    { key: 'stop',    label: 'Stop',    icon: Square,   enabled: up },
    { key: 'restart', label: 'Restart', icon: RotateCw, enabled: true },
  ];

  const handle = (action: ActionKey) => {
    setOpen(false);
    onAction(action);
  };

  return (
    <div ref={ref} className="relative inline-block">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="p-1 rounded hover:bg-muted/40 disabled:cursor-not-allowed disabled:opacity-50 text-muted-foreground hover:text-foreground transition-colors"
        title="Socket actions"
      >
        <MoreVertical className="w-4 h-4" />
      </button>
      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-1 rounded-md border border-border bg-card shadow-md py-1 min-w-[140px] z-20"
        >
          {items.map((it) => (
            <button
              key={it.key}
              type="button"
              role="menuitem"
              disabled={!it.enabled}
              onClick={() => handle(it.key)}
              className="w-full flex items-center gap-2 px-3 py-1.5 text-left text-[12px] text-foreground hover:bg-muted/60 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
            >
              <it.icon className="w-3.5 h-3.5 text-muted-foreground" />
              <span>{it.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
