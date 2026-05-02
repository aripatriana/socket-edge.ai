interface Props {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

/**
 * Pager footer. Minimal controls — Prev/Next + current position —
 * because audit logs rarely need "jump to page 47". Operators filter
 * down to a narrow window and scroll within it.
 */
export function AuditPagination({ page, size, totalElements, totalPages, onPageChange }: Props) {
  const prevDisabled = page <= 0;
  const nextDisabled = page >= totalPages - 1;
  const firstRow = totalElements === 0 ? 0 : page * size + 1;
  const lastRow = Math.min((page + 1) * size, totalElements);

  return (
    <div className="flex items-center justify-between px-4 py-2 border-t border-border bg-card text-[11px] font-mono text-muted-foreground">
      <div>
        {totalElements === 0
          ? 'No entries'
          : `${firstRow.toLocaleString()}–${lastRow.toLocaleString()} of ${totalElements.toLocaleString()}`}
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={prevDisabled}
          onClick={() => onPageChange(page - 1)}
          className="px-2 py-1 border border-border rounded bg-card text-foreground hover:bg-muted/40 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          ← Prev
        </button>
        <span>
          Page <span className="text-foreground">{page + 1}</span>
          {totalPages > 0 && (
            <>
              {' / '}
              <span className="text-foreground">{totalPages}</span>
            </>
          )}
        </span>
        <button
          type="button"
          disabled={nextDisabled}
          onClick={() => onPageChange(page + 1)}
          className="px-2 py-1 border border-border rounded bg-card text-foreground hover:bg-muted/40 disabled:opacity-40 disabled:cursor-not-allowed"
        >
          Next →
        </button>
      </div>
    </div>
  );
}
