import { CheckCircle2, Play, RotateCcw, RefreshCw, Power } from 'lucide-react';
import type { ConfigFileContent } from '../../api/config.types';

interface Props {
  file: ConfigFileContent;
  isDirty: boolean;
  isValidating: boolean;
  isApplying: boolean;
  isReloading: boolean;
  isRestarting: boolean;
  canApply: boolean;
  /**
   * Which post-apply action to show. Each file has exactly one:
   * <ul>
   *   <li>{@code 'reload'} — {@code channel.conf}. Graceful, inflight messages
   *       continue.</li>
   *   <li>{@code 'restart'} — {@code system.conf}, {@code cluster.conf}.
   *       Destructive stop+start. These files govern server/cluster-level
   *       state that the engine can't re-read without a full cold boot.</li>
   * </ul>
   * Keeping this as a single enum (rather than two booleans) makes invalid
   * states unrepresentable — no file should ever offer both buttons.
   */
  postApplyAction: 'reload' | 'restart';
  onValidate: () => void;
  onApply: () => void;
  onReload: () => void;
  onRestart: () => void;
  onDiscard: () => void;
}

/**
 * Toolbar above the editor. Three actions always, fourth varies per file:
 *
 * <ol>
 *   <li><b>Discard</b> — revert draft to last-saved content.</li>
 *   <li><b>Validate</b> — syntax check. Gates Apply + Reload/Restart.</li>
 *   <li><b>Apply</b> — writes the draft to disk + versions it.</li>
 *   <li>Either <b>Reload</b> (channel.conf) or <b>Restart</b>
 *       (system.conf, cluster.conf) — see {@link Props#postApplyAction}.</li>
 * </ol>
 *
 * <p>Restart is visually distinct (destructive tone, Power icon) so
 * operators don't confuse it with Reload. It's currently backed by a
 * stub — UI flow, banners, and audit trail work end-to-end; real
 * restart wiring is pending design.
 */
export function EditorToolbar({
  file,
  isDirty,
  isValidating,
  isApplying,
  isReloading,
  isRestarting,
  canApply,
  postApplyAction,
  onValidate,
  onApply,
  onReload,
  onRestart,
  onDiscard,
}: Props) {
  const nextVersion = file.currentVersion + 1;
  const modifiedDate = file.lastModified > 0 ? new Date(file.lastModified).toLocaleString() : '—';
  const busy = isValidating || isApplying || isReloading || isRestarting;

  return (
    <div className="flex items-start gap-4 px-4 py-3 border-b border-border bg-card flex-wrap">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-mono font-semibold text-[14px] text-foreground">
            {file.name}
          </span>
          {isDirty && (
            <span className="inline-flex items-center px-1.5 py-0.5 rounded border text-[10px] font-semibold tracking-wide bg-amber-50 text-amber-700 border-amber-200">
              UNSAVED CHANGES
            </span>
          )}
        </div>
        <div className="flex items-center gap-2 flex-wrap text-[11px] text-muted-foreground mt-1 font-mono">
          <span>
            current: <span className="text-foreground">v{file.currentVersion}</span>
          </span>
          <span>·</span>
          <span>
            last modified <span className="text-foreground">{modifiedDate}</span>
            {file.lastModifiedBy && (
              <>
                {' by '}
                <span className="text-foreground">{file.lastModifiedBy}</span>
              </>
            )}
          </span>
          <span>·</span>
          <span>
            size <span className="text-foreground">{formatBytes(file.size)}</span>
          </span>
          {isDirty && (
            <>
              <span>·</span>
              <span>
                will save as{' '}
                <span className="text-emerald-600 font-semibold">v{nextVersion}</span>
              </span>
            </>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <Button
          onClick={onDiscard}
          disabled={busy || !isDirty}
          icon={<RotateCcw className="w-3.5 h-3.5" />}
          label="Discard"
          tone="ghost"
        />
        <Button
          onClick={onValidate}
          disabled={busy || !isDirty}
          icon={<CheckCircle2 className="w-3.5 h-3.5" />}
          label={isValidating ? 'Validating…' : 'Validate'}
          tone="default"
          busy={isValidating}
        />
        <Button
          onClick={onApply}
          disabled={busy || !isDirty || !canApply}
          icon={<Play className="w-3.5 h-3.5" />}
          label={isApplying ? 'Applying…' : 'Apply'}
          tone="primary"
          busy={isApplying}
        />
        {postApplyAction === 'reload' ? (
          <Button
            onClick={onReload}
            disabled={busy || !canApply}
            icon={<RefreshCw className="w-3.5 h-3.5" />}
            label={isReloading ? 'Reloading…' : 'Reload'}
            tone="accent"
            busy={isReloading}
          />
        ) : (
          <Button
            onClick={onRestart}
            disabled={busy || !canApply}
            icon={<Power className="w-3.5 h-3.5" />}
            label={isRestarting ? 'Restarting…' : 'Restart'}
            tone="destructive"
            busy={isRestarting}
            title="Full engine stop + start. All TCP connections will drop."
          />
        )}
      </div>
    </div>
  );
}

function Button({
  onClick,
  disabled,
  icon,
  label,
  tone,
  busy,
  title,
}: {
  onClick: () => void;
  disabled?: boolean;
  icon: React.ReactNode;
  label: string;
  tone: 'ghost' | 'default' | 'primary' | 'accent' | 'destructive';
  busy?: boolean;
  title?: string;
}) {
  const base =
    'inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] rounded border transition-colors disabled:cursor-not-allowed disabled:opacity-50';
  const toneClass =
    tone === 'primary'
      ? 'border-primary bg-primary text-primary-foreground hover:bg-primary/90'
      : tone === 'accent'
        ? 'border-sky-300 bg-sky-50 text-sky-800 hover:bg-sky-100'
        : tone === 'destructive'
          ? 'border-red-300 bg-red-50 text-red-800 hover:bg-red-100'
          : tone === 'ghost'
            ? 'border-transparent text-muted-foreground hover:bg-muted/40 hover:text-foreground'
            : 'border-border bg-card text-foreground hover:bg-muted/40';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`${base} ${toneClass}`}
    >
      {busy ? (
        <span className="inline-block w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin" />
      ) : (
        icon
      )}
      <span>{label}</span>
    </button>
  );
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
}
