import type { ConfigValidationMessage } from '../api/config.types';

/**
 * Lightweight HOCON syntax validator — runs in the browser as a fast
 * first-pass check before the Apply request hits the backend.
 *
 * <p>Intentionally narrow scope: syntactic sanity only.
 * <ul>
 *   <li>Braces {}, brackets [], parens () must be balanced and correctly nested.</li>
 *   <li>Double-quoted strings must be closed on the same line (HOCON spec:
 *       no multiline double-quoted strings — triple-quotes are separate).</li>
 *   <li>Triple-quoted strings """...""" must be closed.</li>
 *   <li>Block comments must be closed.</li>
 * </ul>
 *
 * <p><b>Out of scope:</b> type checks (port 1–65535), enum values
 * (mask-strategy ∈ {partial, full}), cross-field rules (quorum ≤ members.length),
 * environment variable resolution, include directives, duplicate keys.
 * These are either engine concerns or belong in a future pass.
 *
 * <p><b>Why not use the `hocon-parser` npm package?</b> Adding a parser
 * pulls a dependency (~50KB min) for a job that is, at this scope, 150
 * lines of hand-rolled code. A real HOCON parser also reports syntax
 * errors in ways that don't map cleanly to editor line/column markers
 * without adapter code. If validation scope ever expands to type checks,
 * revisit this decision.
 *
 * <p><b>Why are error messages ASCII-only?</b> So they render correctly
 * regardless of the editor's Monaco font fallback chain.
 */
export function validateHocon(content: string): ConfigValidationMessage[] {
  const problems: ConfigValidationMessage[] = [];

  // Stack tracks opening tokens so we can report mismatches with the
  // actual opener's line (not just "unbalanced at EOF").
  type OpenToken = { char: '{' | '[' | '('; line: number; column: number };
  const stack: OpenToken[] = [];

  let line = 1;
  let col = 1;
  let i = 0;
  const n = content.length;

  // Parser state. We only track enough to skip over strings/comments
  // correctly — everything else is just brace counting.
  type State =
    | { kind: 'normal' }
    | { kind: 'line-comment' }
    | { kind: 'block-comment'; startLine: number; startCol: number }
    | { kind: 'string'; startLine: number; startCol: number }
    | { kind: 'triple-string'; startLine: number; startCol: number };

  let state: State = { kind: 'normal' };

  // Advance one character, tracking line/col. Must be called for *every*
  // character consumed, including ones inside strings/comments.
  const advance = (): string => {
    const ch = content[i];
    i++;
    if (ch === '\n') {
      line++;
      col = 1;
    } else {
      col++;
    }
    return ch;
  };

  // Peek without advancing. Useful for multi-char tokens like /* and """.
  const peek = (offset = 0): string => content[i + offset] ?? '';

  while (i < n) {
    const startLine = line;
    const startCol = col;

    if (state.kind === 'line-comment') {
      const ch = advance();
      if (ch === '\n') state = { kind: 'normal' };
      continue;
    }

    if (state.kind === 'block-comment') {
      if (peek() === '*' && peek(1) === '/') {
        advance();
        advance();
        state = { kind: 'normal' };
      } else {
        advance();
      }
      continue;
    }

    if (state.kind === 'string') {
      const ch = advance();
      if (ch === '\\') {
        // Escape — consume next char if present, don't interpret it.
        if (i < n) advance();
      } else if (ch === '"') {
        state = { kind: 'normal' };
      } else if (ch === '\n') {
        // HOCON: double-quoted strings cannot span lines. Reopen in normal
        // mode so the rest of the file is still scanned for other errors —
        // otherwise one missing quote silently swallows the rest.
        problems.push(err(
          state.startLine,
          state.startCol,
          'Unterminated string — double-quoted strings cannot span multiple lines',
          'string.unterminated',
        ));
        state = { kind: 'normal' };
      }
      continue;
    }

    if (state.kind === 'triple-string') {
      if (peek() === '"' && peek(1) === '"' && peek(2) === '"') {
        advance();
        advance();
        advance();
        state = { kind: 'normal' };
      } else {
        advance();
      }
      continue;
    }

    // state.kind === 'normal'

    // Triple-quoted string: """..."""
    if (peek() === '"' && peek(1) === '"' && peek(2) === '"') {
      advance();
      advance();
      advance();
      state = { kind: 'triple-string', startLine, startCol };
      continue;
    }

    // Double-quoted string.
    if (peek() === '"') {
      advance();
      state = { kind: 'string', startLine, startCol };
      continue;
    }

    // Line comments: # or //
    if (peek() === '#' || (peek() === '/' && peek(1) === '/')) {
      state = { kind: 'line-comment' };
      continue;
    }

    // Block comment: /* ... */
    if (peek() === '/' && peek(1) === '*') {
      advance();
      advance();
      state = { kind: 'block-comment', startLine, startCol };
      continue;
    }

    const ch = advance();

    if (ch === '{' || ch === '[' || ch === '(') {
      stack.push({ char: ch, line: startLine, column: startCol });
      continue;
    }

    if (ch === '}' || ch === ']' || ch === ')') {
      const expected = matching(ch);
      const top = stack.pop();
      if (!top) {
        problems.push(err(
          startLine,
          startCol,
          `Unexpected closing '${ch}' with no matching opener`,
          'bracket.stray',
        ));
      } else if (top.char !== expected) {
        problems.push(err(
          startLine,
          startCol,
          `Mismatched '${ch}' — opener at line ${top.line} was '${top.char}'`,
          'bracket.mismatch',
        ));
        // Don't push top back — treating it as consumed lets downstream
        // errors be about the *next* mismatch rather than a cascade from
        // the same root cause.
      }
      continue;
    }

    // Everything else (keys, values, whitespace, '=', ':', ',') is out of
    // scope for a syntax-only pass. No action.
  }

  // EOF cleanup — anything still open is an error.
  if (state.kind === 'string') {
    problems.push(err(
      state.startLine,
      state.startCol,
      'Unterminated string at end of file',
      'string.unterminated',
    ));
  } else if (state.kind === 'triple-string') {
    problems.push(err(
      state.startLine,
      state.startCol,
      'Unterminated triple-quoted string at end of file',
      'string.unterminated',
    ));
  } else if (state.kind === 'block-comment') {
    problems.push(err(
      state.startLine,
      state.startCol,
      'Unterminated block comment — missing */',
      'comment.unterminated',
    ));
  }

  for (const open of stack) {
    problems.push(err(
      open.line,
      open.column,
      `Unclosed '${open.char}' — missing '${matching(open.char)}'`,
      'bracket.unclosed',
    ));
  }

  return problems;
}

/** True iff the file name is handled by the FE validator rather than the BE. */
export function isFrontendValidatedFile(fileName: string): boolean {
  return fileName === 'system.conf' || fileName === 'cluster.conf';
}

// ---------------------------------------------------------------------------

function matching(ch: string): string {
  switch (ch) {
    case '{': return '}';
    case '[': return ']';
    case '(': return ')';
    case '}': return '{';
    case ']': return '[';
    case ')': return '(';
    default: return '';
  }
}

function err(
  line: number,
  column: number,
  message: string,
  rule: string,
): ConfigValidationMessage {
  return { line, column, message, severity: 'error', rule };
}
