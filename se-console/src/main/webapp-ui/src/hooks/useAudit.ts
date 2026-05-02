import { useMutation, useQuery } from '@tanstack/react-query';
import { auditApi } from '../api/audit';
import type { AuditFilter, AuditPageResponse, AuditFacetsResponse } from '../api/audit.types';

/**
 * Paginated audit list. Re-fetches whenever filter or page changes;
 * auto-refresh is disabled — audit listings are historical and don't
 * benefit from background polling the way metrics do. The operator
 * re-fetches explicitly via the Refresh button.
 *
 * <p>Short {@code staleTime} so changing filters feels immediately
 * responsive, but back-navigation within the same filter reuses cache.
 */
export function useAuditList(filter: AuditFilter, page: number, size: number) {
  return useQuery<AuditPageResponse>({
    queryKey: ['audit-list', filter, page, size],
    queryFn: () => auditApi.list(filter, page, size),
    staleTime: 10_000,
    refetchOnWindowFocus: false,
  });
}

/**
 * Facets for the filter dropdowns (action + target type values actually
 * present in the DB). Rarely changes — cache for 5 minutes and allow
 * stale-while-revalidate.
 */
export function useAuditFacets() {
  return useQuery<AuditFacetsResponse>({
    queryKey: ['audit-facets'],
    queryFn: () => auditApi.facets(),
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  });
}

/**
 * CSV export as a mutation (not a query) because the user triggers it
 * imperatively and the result is a download side-effect, not data to
 * render. Returns the Blob so the UI can drive the save-as dialog.
 */
export function useAuditExport() {
  return useMutation<Blob, Error, AuditFilter>({
    mutationFn: (filter) => auditApi.exportCsv(filter),
  });
}
