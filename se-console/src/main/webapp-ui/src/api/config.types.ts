// Types for the global Configuration menu (Chat 3c-1e / 3c-1h).

/** One entry in the config file list. */
export interface ConfigFileRef {
  name: string;            // "channel.conf"
  path: string;            // absolute path on disk
  section: string;         // "Core" — for UI grouping
  size: number;            // bytes
  currentVersion: number;  // last applied version
  lastModified: number;    // epoch ms
  lastModifiedBy: string | null;
  exists?: boolean;        // false when whitelisted but file not on disk
}

/** Response of GET /api/config/files. */
export interface ConfigFilesListResponse {
  files: ConfigFileRef[];
}

/** Response of GET /api/config/files/{name}. */
export interface ConfigFileContent {
  name: string;
  path: string;
  content: string;
  currentVersion: number;
  lastModified: number;
  lastModifiedBy: string | null;
  size: number;
}

/** Request body for POST /api/config/validate. */
export interface ValidateRequest {
  name: string;      // file name, e.g. "channel.conf"
  content: string;
}

/** Response of POST /api/config/validate. */
export interface ValidateResponse {
  valid: boolean;
  errors: ConfigValidationMessage[];
  warnings: ConfigValidationMessage[];
  durationMs: number;
}

export interface ConfigValidationMessage {
  line: number;          // 1-based
  column: number;        // 1-based
  message: string;
  severity: 'error' | 'warning';
  rule?: string;         // optional rule identifier
}

/** Request body for PUT /api/config/files/{name}. */
export interface ApplyConfigRequest {
  content: string;
  expectedVersion: number;    // optimistic concurrency: reject if version mismatch
}

/** Response of PUT /api/config/files/{name}. */
export interface ApplyConfigResponse {
  success: boolean;
  newVersion: number;
  engineReloadMs: number;    // 0 when reload is not wired (current state)
  totalMs: number;
  message: string;
}

/** Response of POST /api/config/reload/{name}. */
export interface ReloadConfigResponse {
  success: boolean;
  fileName: string;
  durationMs: number;
  engineReloaded: boolean;   // false while reload is stub'd
  message: string;
}

/**
 * Response of POST /api/config/restart/{name}. Shape mirrors reload so the
 * UI can reuse banner/error patterns. {@code engineRestarted} will be
 * {@code false} until the real restart mechanism is wired — see
 * ConfigRestartController javadoc.
 */
export interface RestartConfigResponse {
  success: boolean;
  fileName: string;
  durationMs: number;
  engineRestarted: boolean;
  message: string;
}
