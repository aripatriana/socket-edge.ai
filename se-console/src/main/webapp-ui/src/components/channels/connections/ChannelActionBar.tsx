import { Play, Square, RotateCw } from 'lucide-react';
import type { ActionKey } from '../../../api/channels.types';

interface Props {
  disabled?: boolean;
  busyAction?: ActionKey | null;
  onAction: (action: ActionKey) => void;
}

/**
 * Channel-level action bar. Three buttons — Start all / Stop all / Restart all.
 * "All" here means "every socket in this channel", which maps to the engine's
 * {@code POST /socket/{action}?name={channelName}} batch endpoint.
 */
export function ChannelActionBar({ disabled, busyAction, onAction }: Props) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground mr-1">
        channel
      </span>
      <ActionButton
        label="Start all"
        icon={<Play className="w-3.5 h-3.5" />}
        tone="default"
        disabled={disabled || busyAction === 'start'}
        busy={busyAction === 'start'}
        onClick={() => onAction('start')}
      />
      <ActionButton
        label="Stop all"
        icon={<Square className="w-3.5 h-3.5" />}
        tone="destructive"
        disabled={disabled || busyAction === 'stop'}
        busy={busyAction === 'stop'}
        onClick={() => onAction('stop')}
      />
      <ActionButton
        label="Restart all"
        icon={<RotateCw className="w-3.5 h-3.5" />}
        tone="default"
        disabled={disabled || busyAction === 'restart'}
        busy={busyAction === 'restart'}
        onClick={() => onAction('restart')}
      />
    </div>
  );
}

function ActionButton({
  label,
  icon,
  tone,
  disabled,
  busy,
  onClick,
}: {
  label: string;
  icon: React.ReactNode;
  tone: 'default' | 'destructive';
  disabled?: boolean;
  busy?: boolean;
  onClick: () => void;
}) {
  const toneClass = tone === 'destructive'
    ? 'border-destructive/40 text-destructive hover:bg-destructive/10'
    : 'border-border text-foreground hover:bg-muted/40';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-[12px] rounded border transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${toneClass}`}
    >
      {busy ? <Spinner /> : icon}
      <span>{label}</span>
    </button>
  );
}

function Spinner() {
  return (
    <span
      className="inline-block w-3.5 h-3.5 border-2 border-muted-foreground border-t-transparent rounded-full animate-spin"
      aria-hidden
    />
  );
}
