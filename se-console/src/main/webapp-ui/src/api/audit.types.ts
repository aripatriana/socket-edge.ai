// Types for the Audit Trail page.

/** One row in the audit table. */
export interface AuditEntry {
  id: number;
  eventTime: number;          // epoch ms, UTC
  userId: number | null;      // null for failed-login on unknown user
  username: string | null;
  action: string;             // e.g. LOGIN, APPLY_CONFIG
  targetType: string | null;  // e.g. user, config_file, channel
  targetId: string | null;
  result: 'success' | 'failed';
  detailsJson: string | null; // raw JSON payload string (not yet parsed)
  sourceIp: string | null;
  userAgent: string | null;
}

/** Response of GET /api/audit. */
export interface AuditPageResponse {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  entries: AuditEntry[];
}

/** Response of GET /api/audit/facets — drives filter dropdowns. */
export interface AuditFacetsResponse {
  actions: string[];
  targetTypes: string[];
  results: string[];
}

/**
 * Filter payload shared between the list + export requests. All fields
 * optional; omitting a field means "no filter on that axis".
 *
 * <p>Dates are ISO-date strings ({@code 2026-04-22}) emitted by a native
 * HTML date input. The backend accepts both bare dates and full ISO
 * instants and treats bare dates as start-of-day UTC.
 */
export interface AuditFilter {
  from?: string;       // YYYY-MM-DD or full ISO
  to?: string;         // YYYY-MM-DD or full ISO
  action?: string;
  result?: 'success' | 'failed' | '';
  targetType?: string;
  username?: string;   // substring, case-insensitive
}
