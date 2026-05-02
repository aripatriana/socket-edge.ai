import { useMemo } from 'react';
import type { ConfigValidationMessage } from '../../api/config.types';

interface Props {
  content: string;
  readOnly?: boolean;
  onChange: (next: string) => void;
  /** Inline error/warning markers — shown as colored left-gutter strips. */
  validationMessages?: ConfigValidationMessage[];
}

/**
 * Lightweight code editor for HOCON / properties / XML config files.
 *
 * <p>No npm dependency — a textarea + line-number gutter + optional
 * validation marker strip. This trades advanced editor features (syntax
 * highlighting while typing, IntelliSense) for zero bundle bloat and
 * zero setup risk. When Monaco/CodeMirror lands later this component's
 * contract stays the same; only the internals change.
 *
 * <p>Keyboard behavior matches native textarea: Tab inserts a tab,
 * Shift+Tab outdents. Scroll is synchronized between gutter and text.
 */
export function ConfigEditor({ content, readOnly, onChange, validationMessages }: Props) {
  // Compute line count live. Using split('\n') so trailing newline doesn't
  // hide the last line number.
  const lines = useMemo(() => content.split('\n'), [content]);
  const totalLines = lines.length;

  // Index validation messages by line for quick gutter lookup.
  const markersByLine = useMemo(() => {
    const m = new Map<number, 'error' | 'warning'>();
    for (const v of validationMessages ?? []) {
      const existing = m.get(v.line);
      if (existing === 'error') continue;   // error wins
      m.set(v.line, v.severity);
    }
    return m;
  }, [validationMessages]);

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (readOnly) return;
    if (e.key === 'Tab') {
      e.preventDefault();
      const ta = e.currentTarget;
      const start = ta.selectionStart;
      const end = ta.selectionEnd;

      if (e.shiftKey) {
        // Outdent: remove up to 2 spaces or 1 tab before the cursor (or
        // each selected line's start).
        outdent(ta, start, end, onChange);
      } else {
        // Insert 2 spaces (matches the engine config conventions).
        const value = ta.value;
        const insert = '  ';
        const next = value.substring(0, start) + insert + value.substring(end);
        onChange(next);
        // Move cursor after the inserted tab, on next microtask.
        requestAnimationFrame(() => {
          ta.selectionStart = ta.selectionEnd = start + insert.length;
        });
      }
    }
  };

  return (
    <div className="relative flex h-full bg-card border-t border-border overflow-hidden font-mono text-[13px]">
      {/* Gutter: line numbers + marker strip */}
      <div
        className="shrink-0 text-right px-2 py-3 text-[11px] text-muted-foreground bg-muted/30 select-none"
        style={{ minWidth: 52 }}
        aria-hidden
      >
        {Array.from({ length: totalLines }, (_, i) => {
          const n = i + 1;
          const severity = markersByLine.get(n);
          return (
            <div
              key={n}
              className="leading-[1.5] relative"
              style={{ height: '1.5em' }}
            >
              <span>{n}</span>
              {severity && (
                <span
                  className={
                    'absolute -right-1.5 top-[3px] bottom-[3px] w-[3px] rounded-sm ' +
                    (severity === 'error' ? 'bg-red-500' : 'bg-amber-500')
                  }
                  title={severity}
                />
              )}
            </div>
          );
        })}
      </div>

      {/* Textarea */}
      <textarea
        value={content}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        readOnly={readOnly}
        spellCheck={false}
        className="flex-1 px-3 py-3 bg-transparent outline-none resize-none text-foreground leading-[1.5] whitespace-pre overflow-auto"
        style={{ tabSize: 2 }}
      />
    </div>
  );
}

/**
 * Shift+Tab handler — removes one level of indent from the selected
 * block of lines (or from the line at the cursor if no selection).
 */
function outdent(
  ta: HTMLTextAreaElement,
  start: number,
  end: number,
  onChange: (v: string) => void,
) {
  const value = ta.value;

  // Expand selection to line boundaries.
  const blockStart = value.lastIndexOf('\n', start - 1) + 1;
  const blockEnd = end > start
    ? (value.indexOf('\n', end - 1) === -1 ? value.length : value.indexOf('\n', end - 1))
    : (value.indexOf('\n', start) === -1 ? value.length : value.indexOf('\n', start));

  const before = value.substring(0, blockStart);
  const block = value.substring(blockStart, blockEnd);
  const after = value.substring(blockEnd);

  const dedented = block
    .split('\n')
    .map((ln) => {
      if (ln.startsWith('  ')) return ln.substring(2);
      if (ln.startsWith('\t')) return ln.substring(1);
      if (ln.startsWith(' ')) return ln.substring(1);
      return ln;
    })
    .join('\n');

  onChange(before + dedented + after);
}
