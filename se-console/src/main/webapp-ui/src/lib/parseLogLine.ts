import type { LogLevel, ParsedLogLine } from '../api/logs.types';

/**
 * Parses a single log line, tolerant of the two common Logback pattern
 * orderings:
 *
 * <pre>
 *   A: %d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n
 *   B: %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
 * </pre>
 *
 * <p>SE-Console's default {@code logback.xml} uses A, but the engine
 * ({@code jalin-isoloadbalancer}) ships with B — the bracketed thread
 * comes before the level. We support both because operators legitimately
 * point the Logs page at either process (the console and the engine
 * write side-by-side into the same {@code log/} directory).
 *
 * <p>Extraction: timestamp for range display, level for filtering and
 * color coding. Thread + logger, if present, are kept in the message
 * so stack traces still read naturally. Lines that match neither
 * pattern return {@code level: 'UNKNOWN'} with the full raw text as
 * message — correct for stack-trace continuation lines.
 *
 * <p>Milliseconds in the timestamp are optional (some Logback configs
 * drop them). Level matching is strict (only the 5 canonical values)
 * so arbitrary bracketed tokens don't get mistaken for levels.
 */

const LEVELS: LogLevel[] = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];
const LEVEL_GROUP = '(TRACE|DEBUG|INFO|WARN|ERROR)';

// Pattern A: yyyy-MM-dd HH:mm:ss[.SSS]  LEVEL  rest
// Matches the legacy console format — level immediately after timestamp.
const LINE_PATTERN_LEVEL_FIRST = new RegExp(
  '^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?)'
  + '\\s+' + LEVEL_GROUP + '\\s+(.*)$',
);

// Pattern B: yyyy-MM-dd HH:mm:ss[.SSS]  [thread]  LEVEL  rest
// Matches the engine format — thread bracket before level. Thread
// content is non-greedy and forbids nested brackets, so we don't
// accidentally swallow bracketed tokens from the message itself.
const LINE_PATTERN_THREAD_FIRST = new RegExp(
  '^(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,3})?)'
  + '\\s+\\[([^\\]]+)\\]'
  + '\\s+' + LEVEL_GROUP + '\\s+(.*)$',
);

export function parseLogLine(raw: string, key: string): ParsedLogLine {
  // Try the engine-style pattern first because it's strictly more
  // specific (requires the thread bracket). If we tried the simpler
  // level-first pattern first, an engine line like "... [thread] INFO"
  // would fail the simple regex and only the fallback would match —
  // but that's what happens anyway for engine lines under pattern A,
  // so order doesn't strictly matter for correctness; it's just
  // slightly more efficient to try the more-specific one first for
  // the more-common case in this codebase.
  const threadFirst = LINE_PATTERN_THREAD_FIRST.exec(raw);
  if (threadFirst) {
    const [, datePart, timePart, thread, levelPart, rest] = threadFirst;
    const level = (LEVELS.includes(levelPart as LogLevel) ? levelPart : 'UNKNOWN') as LogLevel;
    const iso = `${datePart}T${timePart}`;
    const ts = Date.parse(iso);
    return {
      key,
      timestampMs: Number.isFinite(ts) ? ts : null,
      timeText: timePart,
      level,
      raw,
      // Preserve [thread] in the message so operators can still see
      // which thread logged the line — it's useful context even
      // though we don't render it in a dedicated column. Avoids
      // forcing a schema change to ParsedLogLine.
      message: `[${thread}] ${rest}`,
    };
  }

  const levelFirst = LINE_PATTERN_LEVEL_FIRST.exec(raw);
  if (levelFirst) {
    const [, datePart, timePart, levelPart, rest] = levelFirst;
    const level = (LEVELS.includes(levelPart as LogLevel) ? levelPart : 'UNKNOWN') as LogLevel;
    const iso = `${datePart}T${timePart}`;
    const ts = Date.parse(iso);
    return {
      key,
      timestampMs: Number.isFinite(ts) ? ts : null,
      timeText: timePart,
      level,
      raw,
      message: rest,
    };
  }

  return {
    key,
    timestampMs: null,
    timeText: '',
    level: 'UNKNOWN',
    raw,
    message: raw,
  };
}

export function parseBatch(rawLines: string[], prefix: string): ParsedLogLine[] {
  const out = new Array<ParsedLogLine>(rawLines.length);
  for (let i = 0; i < rawLines.length; i++) {
    out[i] = parseLogLine(rawLines[i], `${prefix}-${i}`);
  }
  return out;
}

export function timeRange(parsed: ParsedLogLine[]): { fromMs: number | null; toMs: number | null } {
  let fromMs: number | null = null;
  let toMs: number | null = null;
  for (const p of parsed) {
    if (p.timestampMs == null) continue;
    if (fromMs === null) fromMs = p.timestampMs;
    toMs = p.timestampMs;
  }
  return { fromMs, toMs };
}
