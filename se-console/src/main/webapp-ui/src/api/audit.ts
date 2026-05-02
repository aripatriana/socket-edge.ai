import { api } from './client';
import { useAuthStore } from '../stores/authStore';
import type { AuditPageResponse, AuditFacetsResponse, AuditFilter } from './audit.types';

/**
 * Build a query string from a filter payload, dropping empty values so
 * the URL stays clean ({@code ?from=2026-04-22} rather than
 * {@code ?from=&to=&action=...}). Uses URLSearchParams for correct
 * percent-encoding — audit usernames can contain arbitrary characters.
 */
function buildQuery(filter: AuditFilter, extra?: Record<string, string | number>): string {
  const sp = new URLSearchParams();
  if (filter.from) sp.set('from', filter.from);
  if (filter.to) sp.set('to', filter.to);
  if (filter.action) sp.set('action', filter.action);
  if (filter.result) sp.set('result', filter.result);
  if (filter.targetType) sp.set('targetType', filter.targetType);
  if (filter.username) sp.set('username', filter.username);
  if (extra) {
    for (const [k, v] of Object.entries(extra)) sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export const auditApi = {
  list(filter: AuditFilter, page: number, size: number): Promise<AuditPageResponse> {
    return api.get<AuditPageResponse>(`/api/audit${buildQuery(filter, { page, size })}`);
  },

  facets(): Promise<AuditFacetsResponse> {
    return api.get<AuditFacetsResponse>('/api/audit/facets');
  },

  /**
   * CSV export. The `api` client is JSON-oriented, so this is a plain
   * fetch with the auth header attached manually — we want a Blob, not
   * a parsed JSON body. Returns the Blob so the caller can trigger a
   * download via an anchor tag.
   *
   * <p>Auth token is read from the shared auth store, same source the
   * {@code api} client uses — a small duplication, but adding a Blob
   * mode to the shared client would leak a lot of JSON assumptions.
   */
  async exportCsv(filter: AuditFilter): Promise<Blob> {
    const token = useAuthStore.getState().accessToken;
    const res = await fetch(`/api/audit/export${buildQuery(filter)}`, {
      method: 'GET',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) {
      // Try to include the server's error message if it's JSON.
      let detail = '';
      try {
        const body = await res.json();
        detail = body?.message || body?.error || '';
      } catch {
        // non-JSON body — ignore
      }
      throw new Error(
        `Export failed: ${res.status} ${res.statusText}${detail ? ' — ' + detail : ''}`,
      );
    }
    return res.blob();
  },
};
