// Types for the Configuration History menu (Chat 3c-1g).

/** Version metadata entry shown in the timeline. */
export interface ConfigVersionSummary {
  version: number;
  fileName: string;
  contentSha: string;
  author: string | null;
  description: string | null;
  appliedAt: number | null;          // epoch ms
  applyDurationMs: number | null;
  applyResult: 'success' | 'failed' | 'rolled_back';
  rolledBackFromVersion: number | null;
  milestone: boolean;
  size: number;
}

/** Full version with content — served by GET /history/{file}/{v}. */
export interface ConfigVersionDetail extends ConfigVersionSummary {
  content: string;
}

/** List response — GET /history/{file}. */
export interface ConfigHistoryResponse {
  fileName: string;
  currentVersion: number | null;
  versions: ConfigVersionSummary[];
}

/** Diff response — GET /diff/{file}/{v1}/{v2}. */
export interface ConfigDiffResponse {
  fileName: string;
  v1: ConfigVersionSummary;
  v2: ConfigVersionSummary;
  v1Content: string;
  v2Content: string;
  v1Lines: number;
  v2Lines: number;
}

/** Rollback response — POST /rollback/{file}/{v}. */
export interface ConfigRollbackResponse {
  success: boolean;
  fileName: string;
  newVersion: number;
  rolledBackFrom: number;
  applyDurationMs: number;
}

// --- computed diff view types ---------------------------------------------

/** One diff line after Myers-style LCS, ready to render. */
export interface DiffLine {
  /** 'context' shared, 'add' only in v2, 'del' only in v1 */
  kind: 'context' | 'add' | 'del';
  /** line number in v1 (null when kind === 'add') */
  v1Line: number | null;
  /** line number in v2 (null when kind === 'del') */
  v2Line: number | null;
  text: string;
}

export interface DiffSummary {
  added: number;
  removed: number;
  contextLines: number;
}
