// Types for the global Logs menu (Chat 3c-1f).

export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'UNKNOWN';

/** Raw tail response from SE-Console backend. */
export interface LogTailResponse {
  fileName: string;
  absolutePath: string;
  lines: string[];        // oldest → newest
  lineCount: number;
  fileSize: number;
  truncated: boolean;
  error: string | null;
}

/** A single log line after client-side parsing. */
export interface ParsedLogLine {
  /** stable key for React list rendering — derived from file offset or index */
  key: string;
  /** epoch ms if we could parse a timestamp, null otherwise */
  timestampMs: number | null;
  /** HH:mm:ss.SSS portion for display — falls back to empty string */
  timeText: string;
  /** parsed level, UNKNOWN if we couldn't find one */
  level: LogLevel;
  /** whole raw line, useful for search and fallback display */
  raw: string;
  /** message portion (line minus timestamp+level prefix) */
  message: string;
}

/** Polling interval options exposed in the UI. */
export type PollIntervalKey = 'off' | '3s' | '5s' | '10s';

export const POLL_INTERVAL_MS: Record<PollIntervalKey, number | false> = {
  off: false,
  '3s': 3_000,
  '5s': 5_000,
  '10s': 10_000,
};

/** Line count options in the UI dropdown. */
export const LINE_COUNT_OPTIONS = [500, 1000, 2000, 5000] as const;
export type LineCount = typeof LINE_COUNT_OPTIONS[number];
