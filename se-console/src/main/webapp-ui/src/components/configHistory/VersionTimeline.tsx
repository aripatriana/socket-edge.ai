import type { ConfigVersionSummary } from '../../api/configHistory.types';

interface Props {
  versions: ConfigVersionSummary[];
  currentVersion: number | null;
  selectedVersion: number | null;
  onSelect: (version: number) => void;
}

/**
 * Timeline of versions, newest at the top. A vertical line runs through
 * every item; each item has a colored dot indicating its state:
 *
 * <ul>
 *   <li><b>current</b> — accent dot, "CURRENT" tag</li>
 *   <li><b>rollback</b> — amber dot, "ROLLED BACK" tag, shows source version</li>
 *   <li><b>regular</b> — neutral dot</li>
 * </ul>
 *
 * <p>Click a version to select it. Diff panel on the right updates
 * to show current↔selected.
 */
export function VersionTimeline({
  versions,
  currentVersion,
  selectedVersion,
  onSelect,
}: Props) {
  if (versions.length === 0) {
    return (
      <aside
        className="flex flex-col bg-card border-r border-border p-4"
        style={{ width: 280 }}
      >
        <div className="text-[13px] font-semibold mb-1">History</div>
        <div className="text-[11px] italic text-muted-foreground">
          No versions applied yet.
        </div>
      </aside>
    );
  }

  return (
    <aside
      className="flex flex-col bg-card border-r border-border overflow-hidden"
      style={{ width: 280 }}
    >
      <header className="px-4 py-3 border-b border-border">
        <div className="text-[13px] font-semibold text-foreground">History</div>
        <div className="text-[11px] text-muted-foreground mt-0.5 font-mono">
          {versions.length} version{versions.length === 1 ? '' : 's'}
        </div>
      </header>

      <ol className="flex-1 overflow-y-auto py-2 relative">
        <span
          className="absolute left-[22px] top-3 bottom-3 w-px bg-border pointer-events-none"
          aria-hidden
        />
        {versions.map((v) => (
          <TimelineItem
            key={v.version}
            version={v}
            isCurrent={v.version === currentVersion}
            isSelected={v.version === selectedVersion}
            onClick={() => onSelect(v.version)}
          />
        ))}
      </ol>
    </aside>
  );
}

// ---------------------------------------------------------------------------

function TimelineItem({
  version,
  isCurrent,
  isSelected,
  onClick,
}: {
  version: ConfigVersionSummary;
  isCurrent: boolean;
  isSelected: boolean;
  onClick: () => void;
}) {
  const dotClass = isCurrent
    ? 'bg-primary border-primary ring-4 ring-primary/20'
    : version.rolledBackFromVersion !== null
      ? 'bg-amber-500 border-amber-500'
      : version.applyResult === 'failed'
        ? 'bg-red-500 border-red-500'
        : 'bg-muted-foreground/60 border-muted-foreground/60';

  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className={[
          'w-full text-left px-4 py-2.5 pl-[44px] relative border-l-2 transition-colors',
          isSelected
            ? 'bg-muted/50 border-primary text-foreground'
            : 'border-transparent text-foreground hover:bg-muted/30',
        ].join(' ')}
      >
        <span
          className={`absolute left-[16px] top-[14px] w-[13px] h-[13px] rounded-full border-2 ${dotClass}`}
          aria-hidden
        />

        <div className="flex items-center gap-2 mb-0.5">
          <span className="font-mono font-semibold text-[13px] text-foreground">
            v{version.version}
          </span>
          <VersionTag version={version} isCurrent={isCurrent} />
        </div>

        <div className="text-[10px] font-mono text-muted-foreground">
          {version.appliedAt ? formatDateTime(version.appliedAt) : '—'}
        </div>

        {version.description && (
          <div className="text-[11px] text-muted-foreground mt-1 line-clamp-2">
            {version.description}
          </div>
        )}

        <div className="flex items-center gap-2 mt-1 text-[10px] font-mono text-muted-foreground">
          {version.author && (
            <span className="inline-flex items-center gap-1">
              <span className="w-3.5 h-3.5 rounded-full bg-primary text-primary-foreground text-[8px] font-semibold grid place-items-center">
                {version.author.charAt(0).toUpperCase()}
              </span>
              <span>{version.author}</span>
            </span>
          )}
          <span className="ml-auto">{formatBytes(version.size)}</span>
        </div>
      </button>
    </li>
  );
}

function VersionTag({
  version,
  isCurrent,
}: {
  version: ConfigVersionSummary;
  isCurrent: boolean;
}) {
  if (isCurrent) {
    return <Tag className="bg-primary text-primary-foreground">CURRENT</Tag>;
  }
  if (version.rolledBackFromVersion !== null) {
    return <Tag className="bg-amber-500/15 text-amber-700 border border-amber-300">ROLLED BACK</Tag>;
  }
  if (version.applyResult === 'failed') {
    return <Tag className="bg-red-500/15 text-red-700 border border-red-300">FAILED</Tag>;
  }
  // Baseline (v0) takes precedence over the generic milestone badge: even
  // though the seeder marks baselines with milestone=true for visual
  // distinction, "BASELINE" is the more informative label for operators.
  if (version.version === 0) {
    return <Tag className="bg-muted text-muted-foreground border border-border">BASELINE</Tag>;
  }
  if (version.milestone) {
    return <Tag className="bg-sky-500/15 text-sky-700 border border-sky-300">MILESTONE</Tag>;
  }
  return null;
}

function Tag({ children, className }: { children: React.ReactNode; className: string }) {
  return (
    <span className={`px-1.5 py-[1px] rounded text-[9px] font-semibold uppercase tracking-wider ${className}`}>
      {children}
    </span>
  );
}

function formatDateTime(ms: number): string {
  const d = new Date(ms);
  const y = d.getFullYear();
  const M = String(d.getMonth() + 1).padStart(2, '0');
  const D = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${M}-${D} ${h}:${m}`;
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
}
