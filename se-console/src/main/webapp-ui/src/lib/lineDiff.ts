import type { DiffLine, DiffSummary } from '../api/configHistory.types';

/**
 * Simple LCS-based line diff. Suitable for config files up to a few hundred
 * lines — memory is O(M*N) which is fine for our use.
 *
 * <p>Produces a flat list of {@link DiffLine}s in source order, where each
 * row is either context (unchanged), add (only in v2), or del (only in v1).
 *
 * <p>Newlines are preserved as line separators but stripped from the {@code
 * text} of each row. Trailing newline produces a final empty line, which is
 * elided to match what operators expect from {@code diff -u}.
 */
export function computeLineDiff(v1Content: string, v2Content: string): {
  lines: DiffLine[];
  summary: DiffSummary;
} {
  const a = splitLines(v1Content);
  const b = splitLines(v2Content);

  const lcs = buildLcsTable(a, b);
  const out: DiffLine[] = [];
  let added = 0;
  let removed = 0;
  let context = 0;

  walkBack(a, b, lcs, out);

  for (const row of out) {
    if (row.kind === 'add') added++;
    else if (row.kind === 'del') removed++;
    else context++;
  }

  return { lines: out, summary: { added, removed, contextLines: context } };
}

// ---------------------------------------------------------------------------

function splitLines(s: string): string[] {
  if (s.length === 0) return [];
  // Strip a single trailing newline so we don't emit a phantom empty line.
  const trimmed = s.endsWith('\n') ? s.slice(0, -1) : s;
  return trimmed.split('\n');
}

/** Standard LCS length table. `lcs[i][j]` = length of LCS of a[0..i) vs b[0..j). */
function buildLcsTable(a: string[], b: string[]): Int32Array[] {
  const m = a.length;
  const n = b.length;
  const table: Int32Array[] = new Array(m + 1);
  for (let i = 0; i <= m; i++) table[i] = new Int32Array(n + 1);

  for (let i = 1; i <= m; i++) {
    const ai = a[i - 1];
    const row = table[i];
    const prev = table[i - 1];
    for (let j = 1; j <= n; j++) {
      if (ai === b[j - 1]) {
        row[j] = prev[j - 1] + 1;
      } else {
        const up = prev[j];
        const left = row[j - 1];
        row[j] = up >= left ? up : left;
      }
    }
  }
  return table;
}

/**
 * Backtrack through the LCS table to produce the diff. We walk from
 * (m, n) towards (0, 0), emitting rows, then reverse at the end.
 */
function walkBack(a: string[], b: string[], lcs: Int32Array[], out: DiffLine[]) {
  let i = a.length;
  let j = b.length;

  // Temporary buffer — we'll reverse it into `out` at the end.
  const reversed: DiffLine[] = [];

  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) {
      reversed.push({ kind: 'context', v1Line: i, v2Line: j, text: a[i - 1] });
      i--; j--;
    } else if (lcs[i - 1][j] >= lcs[i][j - 1]) {
      reversed.push({ kind: 'del', v1Line: i, v2Line: null, text: a[i - 1] });
      i--;
    } else {
      reversed.push({ kind: 'add', v1Line: null, v2Line: j, text: b[j - 1] });
      j--;
    }
  }
  while (i > 0) {
    reversed.push({ kind: 'del', v1Line: i, v2Line: null, text: a[i - 1] });
    i--;
  }
  while (j > 0) {
    reversed.push({ kind: 'add', v1Line: null, v2Line: j, text: b[j - 1] });
    j--;
  }

  for (let k = reversed.length - 1; k >= 0; k--) {
    out.push(reversed[k]);
  }
}
