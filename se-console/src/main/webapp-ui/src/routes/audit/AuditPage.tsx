import { useState, useCallback } from 'react';
import { Download } from 'lucide-react';
import { AppShell } from '../../components/layout/AppShell';
import { AuditFilterBar } from '../../components/audit/AuditFilterBar';
import { AuditTable } from '../../components/audit/AuditTable';
import { AuditPagination } from '../../components/audit/AuditPagination';
import { useAuditList, useAuditFacets, useAuditExport } from '../../hooks/useAudit';
import type { AuditFilter } from '../../api/audit.types';

const DEFAULT_PAGE_SIZE = 50;

/**
 * Audit Trail page — ADMIN-only. Lists append-only audit entries with
 * filtering and CSV export.
 *
 * <p>Layout: filter bar at top, table in middle (scrollable), pagination
 * at bottom. Export button sits next to the filter bar's Apply/Clear so
 * operators can export what they currently see, not "export the world".
 */
export function AuditPage() {
  const [filter, setFilter] = useState<AuditFilter>({});
  const [page, setPage] = useState(0);

  const { data, isLoading, isFetching, refetch, isError, error } = useAuditList(
    filter,
    page,
    DEFAULT_PAGE_SIZE,
  );
  const { data: facets } = useAuditFacets();
  const exportMut = useAuditExport();

  const [exportError, setExportError] = useState<string | null>(null);
  const [exportNote, setExportNote] = useState<string | null>(null);

  const onFilterChange = useCallback((next: AuditFilter) => {
    setFilter(next);
    setPage(0); // reset to first page on new filter
  }, []);

  const onExport = async () => {
    setExportError(null);
    setExportNote(null);
    try {
      const blob = await exportMut.mutateAsync(filter);
      // Trigger browser download. Filename from Content-Disposition is
      // the authoritative one, but browsers don't expose it cleanly in
      // fetch responses without parsing headers — we set a sensible
      // client-side name instead.
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      // Revoke on the next tick so the browser has time to start the download.
      setTimeout(() => URL.revokeObjectURL(url), 1_000);
      setExportNote('CSV export ready — check your downloads folder.');
    } catch (err) {
      setExportError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <AppShell>
      <div className="flex flex-col h-full -m-5" style={{ height: 'calc(100vh - 52px)' }}>
        <header className="flex items-center gap-3 px-4 py-3 border-b border-border bg-card">
          <div className="flex-1">
            <div className="text-[14px] font-semibold text-foreground">Audit Trail</div>
            <div className="text-[11px] text-muted-foreground font-mono mt-0.5">
              Append-only log of console actions · admin only
            </div>
          </div>
          <button
            type="button"
            onClick={onExport}
            disabled={exportMut.isPending}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] border border-border rounded bg-card text-foreground hover:bg-muted/40 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {exportMut.isPending ? (
              <span className="inline-block w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin" />
            ) : (
              <Download className="w-3.5 h-3.5" />
            )}
            <span>{exportMut.isPending ? 'Exporting…' : 'Export CSV'}</span>
          </button>
        </header>

        {(exportError || exportNote) && (
          <div
            role="status"
            className={`border-b px-4 py-2 text-[12px] flex items-start gap-2 ${
              exportError
                ? 'bg-red-50 border-red-200 text-red-800'
                : 'bg-emerald-50 border-emerald-200 text-emerald-800'
            }`}
          >
            <span className="flex-1 font-mono">{exportError ?? exportNote}</span>
            <button
              type="button"
              onClick={() => { setExportError(null); setExportNote(null); }}
              className="opacity-60 hover:opacity-100 text-[14px]"
              aria-label="Dismiss"
            >
              ×
            </button>
          </div>
        )}

        <AuditFilterBar
          filter={filter}
          facets={facets}
          isFetching={isFetching}
          onFilterChange={onFilterChange}
          onRefresh={() => refetch()}
        />

        {isError && (
          <div className="m-4 rounded-md border border-destructive bg-destructive/10 p-4 text-[13px]">
            <div className="font-semibold text-destructive">Failed to load audit entries</div>
            <div className="text-[11px] text-muted-foreground mt-1 font-mono">
              {error instanceof Error ? error.message : String(error)}
            </div>
          </div>
        )}

        <AuditTable entries={data?.entries ?? []} isLoading={isLoading} />

        <AuditPagination
          page={data?.page ?? page}
          size={data?.size ?? DEFAULT_PAGE_SIZE}
          totalElements={data?.totalElements ?? 0}
          totalPages={data?.totalPages ?? 0}
          onPageChange={setPage}
        />
      </div>
    </AppShell>
  );
}
