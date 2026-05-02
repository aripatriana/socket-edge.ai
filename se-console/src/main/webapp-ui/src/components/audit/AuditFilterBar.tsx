import { useState, useEffect } from 'react';
import { Search, X, RefreshCw } from 'lucide-react';
import type { AuditFilter, AuditFacetsResponse } from '../../api/audit.types';

interface Props {
  filter: AuditFilter;
  facets: AuditFacetsResponse | undefined;
  isFetching: boolean;
  onFilterChange: (next: AuditFilter) => void;
  onRefresh: () => void;
}

/**
 * Filter bar above the audit table. Applies on "Apply" click, not on
 * every keystroke — audit queries can be expensive (they scan the
 * index) and typing in the username field would otherwise fire a
 * request per character.
 *
 * <p>Dates are native {@code <input type="date">} — good-enough UX
 * without pulling a date-picker dependency. The backend happily accepts
 * {@code YYYY-MM-DD} as start-of-day UTC.
 */
export function AuditFilterBar({
  filter,
  facets,
  isFetching,
  onFilterChange,
  onRefresh,
}: Props) {
  // Draft state so the user can type/pick multiple fields before
  // committing. Reset to current filter whenever the parent changes it
  // programmatically (e.g. via Clear).
  const [draft, setDraft] = useState<AuditFilter>(filter);
  useEffect(() => setDraft(filter), [filter]);

  const isDirty =
    draft.from !== filter.from ||
    draft.to !== filter.to ||
    draft.action !== filter.action ||
    draft.result !== filter.result ||
    draft.targetType !== filter.targetType ||
    draft.username !== filter.username;

  const hasAnyFilter =
    !!filter.from || !!filter.to || !!filter.action || !!filter.result ||
    !!filter.targetType || !!filter.username;

  const apply = () => onFilterChange(draft);
  const clear = () => {
    const empty: AuditFilter = {};
    setDraft(empty);
    onFilterChange(empty);
  };

  return (
    <div className="flex items-end gap-2 flex-wrap px-4 py-3 border-b border-border bg-card">
      <Field label="From">
        <input
          type="date"
          value={draft.from ?? ''}
          onChange={(e) => setDraft((d) => ({ ...d, from: e.target.value || undefined }))}
          className="px-2 py-1 text-[12px] border border-border rounded bg-background w-[140px]"
        />
      </Field>
      <Field label="To">
        <input
          type="date"
          value={draft.to ?? ''}
          onChange={(e) => setDraft((d) => ({ ...d, to: e.target.value || undefined }))}
          className="px-2 py-1 text-[12px] border border-border rounded bg-background w-[140px]"
        />
      </Field>

      <Field label="Action">
        <select
          value={draft.action ?? ''}
          onChange={(e) => setDraft((d) => ({ ...d, action: e.target.value || undefined }))}
          className="px-2 py-1 text-[12px] border border-border rounded bg-background w-[160px]"
        >
          <option value="">All</option>
          {facets?.actions.map((a) => (
            <option key={a} value={a}>{a}</option>
          ))}
        </select>
      </Field>

      <Field label="Result">
        <select
          value={draft.result ?? ''}
          onChange={(e) => setDraft((d) => ({ ...d, result: (e.target.value || undefined) as AuditFilter['result'] }))}
          className="px-2 py-1 text-[12px] border border-border rounded bg-background w-[110px]"
        >
          <option value="">All</option>
          <option value="success">Success</option>
          <option value="failed">Failed</option>
        </select>
      </Field>

      <Field label="Target">
        <select
          value={draft.targetType ?? ''}
          onChange={(e) => setDraft((d) => ({ ...d, targetType: e.target.value || undefined }))}
          className="px-2 py-1 text-[12px] border border-border rounded bg-background w-[130px]"
        >
          <option value="">All</option>
          {facets?.targetTypes.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
      </Field>

      <Field label="User">
        <div className="relative">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground pointer-events-none" />
          <input
            type="text"
            value={draft.username ?? ''}
            onChange={(e) => setDraft((d) => ({ ...d, username: e.target.value || undefined }))}
            onKeyDown={(e) => {
              if (e.key === 'Enter') apply();
            }}
            placeholder="substring"
            className="pl-7 pr-2 py-1 text-[12px] border border-border rounded bg-background w-[160px]"
          />
        </div>
      </Field>

      <div className="flex items-center gap-1 ml-auto">
        {hasAnyFilter && (
          <button
            type="button"
            onClick={clear}
            className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] border border-border rounded bg-card text-muted-foreground hover:bg-muted/40"
          >
            <X className="w-3 h-3" />
            Clear
          </button>
        )}
        <button
          type="button"
          onClick={apply}
          disabled={!isDirty}
          className="inline-flex items-center gap-1 px-3 py-1 text-[12px] border border-primary bg-primary text-primary-foreground rounded hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Apply
        </button>
        <button
          type="button"
          onClick={onRefresh}
          className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] border border-border rounded bg-card text-foreground hover:bg-muted/40"
          title="Refresh"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
        </button>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
        {label}
      </span>
      {children}
    </label>
  );
}
