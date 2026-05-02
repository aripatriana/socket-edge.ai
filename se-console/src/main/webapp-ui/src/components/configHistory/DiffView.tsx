import { useMemo } from 'react';
import { computeLineDiff } from '../../lib/lineDiff';
import type { DiffLine } from '../../api/configHistory.types';

interface Props {
  v1Content: string;
  v2Content: string;
  /** v1 label for the hunk headers, e.g. "v3" or "CURRENT (v5)". */
  v1Label: string;
  /** v2 label, e.g. "v4". */
  v2Label: string;
}

/**
 * Unified-line diff view — context lines in white, {@code +} lines green,
 * {@code -} lines red. Line numbers are shown per-side so operators can
 * cross-reference against a separate editor session.
 *
 * <p>At config-file sizes (few hundred lines) the O(M*N) LCS comfortably
 * runs under a millisecond. For larger files we'd want a proper
 * Myers-with-snake optimization — not needed here.
 */
export function DiffView({ v1Content, v2Content, v1Label, v2Label }: Props) {
  const { lines, summary } = useMemo(
    () => computeLineDiff(v1Content, v2Content),
    [v1Content, v2Content],
  );

  if (v1Content === v2Content) {
    return (
      <div className="flex-1 grid place-items-center text-[12px] italic text-muted-foreground p-8">
        No differences between {v1Label} and {v2Label}.
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Hunk header */}
      <header className="flex items-center gap-3 px-4 py-2 border-b border-border bg-muted/30 text-[11px] font-mono">
        <span className="text-foreground">{v1Label}</span>
        <span className="text-muted-foreground">→</span>
        <span className="text-foreground">{v2Label}</span>
        <span className="ml-auto">
          <span className="text-emerald-600 font-semibold">+{summary.added}</span>
          <span className="text-muted-foreground mx-2">·</span>
          <span className="text-red-600 font-semibold">−{summary.removed}</span>
          <span className="text-muted-foreground mx-2">·</span>
          <span className="text-muted-foreground">{summary.contextLines} context</span>
        </span>
      </header>

      {/* Diff body */}
      <div className="flex-1 overflow-auto font-mono text-[12px] leading-[1.55] bg-card">
        {lines.map((line, i) => (
          <DiffRow key={i} line={line} />
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function DiffRow({ line }: { line: DiffLine }) {
  const { rowClass, markerClass, marker, numClass } = TONE[line.kind];
  return (
    <div className={`grid grid-cols-[48px_48px_24px_1fr] ${rowClass}`}>
      <span className={`text-right pr-2 select-none ${numClass}`}>
        {line.v1Line ?? ''}
      </span>
      <span className={`text-right pr-2 select-none ${numClass}`}>
        {line.v2Line ?? ''}
      </span>
      <span className={`text-center select-none font-semibold ${markerClass}`}>
        {marker}
      </span>
      <span className="pl-2 pr-3 whitespace-pre-wrap break-all text-foreground">
        {line.text}
      </span>
    </div>
  );
}

const TONE = {
  context: {
    rowClass: 'bg-card',
    markerClass: 'text-muted-foreground',
    marker: '',
    numClass: 'text-muted-foreground/70 bg-muted/30',
  },
  add: {
    rowClass: 'bg-emerald-50',
    markerClass: 'text-emerald-700',
    marker: '+',
    numClass: 'text-emerald-700 bg-emerald-100/60',
  },
  del: {
    rowClass: 'bg-red-50',
    markerClass: 'text-red-700',
    marker: '−',
    numClass: 'text-red-700 bg-red-100/60',
  },
} as const;
