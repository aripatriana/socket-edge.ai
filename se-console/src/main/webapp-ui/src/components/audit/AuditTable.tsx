import { useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import type { AuditEntry } from '../../api/audit.types';

interface Props {
  entries: AuditEntry[];
  isLoading: boolean;
}

/**
 * Table of audit entries — newest first. Details JSON is hidden behind
 * a per-row chevron to avoid making the table height balloon for
 * entries with big payloads (config diffs, reload reports).
 */
export function AuditTable({ entries, isLoading }: Props) {
  if (isLoading && entries.length === 0) {
    return (
      <div className="flex-1 grid place-items-center text-[12px] text-muted-foreground">
        Loading audit entries…
      </div>
    );
  }
  if (!isLoading && entries.length === 0) {
    return (
      <div className="flex-1 grid place-items-center p-6">
        <div className="rounded-md border border-dashed border-border bg-muted/20 px-8 py-10 text-center text-[13px] text-muted-foreground max-w-md">
          <div className="font-semibold text-foreground mb-2">No audit entries</div>
          <div className="text-[11px] font-mono">
            No rows match the current filters. Try widening the date range or
            clearing action/target filters.
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-auto">
      <table className="w-full text-[12px]">
        <thead className="sticky top-0 bg-card border-b border-border z-10">
          <tr className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">
            <th className="w-6" />
            <th className="text-left px-3 py-2 font-semibold">Time (UTC)</th>
            <th className="text-left px-3 py-2 font-semibold">User</th>
            <th className="text-left px-3 py-2 font-semibold">Action</th>
            <th className="text-left px-3 py-2 font-semibold">Target</th>
            <th className="text-left px-3 py-2 font-semibold">Result</th>
            <th className="text-left px-3 py-2 font-semibold">IP</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <Row key={e.id} entry={e} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Row({ entry }: { entry: AuditEntry }) {
  const [open, setOpen] = useState(false);
  const hasDetails = !!entry.detailsJson || !!entry.userAgent;

  return (
    <>
      <tr className="border-b border-border hover:bg-muted/30">
        <td className="w-6 px-2 align-top pt-2.5">
          {hasDetails && (
            <button
              type="button"
              onClick={() => setOpen((v) => !v)}
              className="text-muted-foreground hover:text-foreground"
              aria-label={open ? 'Hide details' : 'Show details'}
              aria-expanded={open}
            >
              {open ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
            </button>
          )}
        </td>
        <td className="px-3 py-2 font-mono text-[11px] whitespace-nowrap">
          {formatTime(entry.eventTime)}
        </td>
        <td className="px-3 py-2 font-mono">
          {entry.username ?? <span className="text-muted-foreground italic">—</span>}
        </td>
        <td className="px-3 py-2 font-mono">
          <ActionBadge action={entry.action} />
        </td>
        <td className="px-3 py-2 font-mono text-[11px]">
          {entry.targetType ? (
            <>
              <span className="text-muted-foreground">{entry.targetType}</span>
              {entry.targetId && (
                <>
                  <span className="text-muted-foreground mx-1">·</span>
                  <span>{entry.targetId}</span>
                </>
              )}
            </>
          ) : (
            <span className="text-muted-foreground italic">—</span>
          )}
        </td>
        <td className="px-3 py-2">
          <ResultBadge result={entry.result} />
        </td>
        <td className="px-3 py-2 font-mono text-[11px] text-muted-foreground">
          {entry.sourceIp ?? '—'}
        </td>
      </tr>
      {open && hasDetails && (
        <tr className="bg-muted/10 border-b border-border">
          <td />
          <td colSpan={6} className="px-3 py-2">
            <DetailsBlock entry={entry} />
          </td>
        </tr>
      )}
    </>
  );
}

function DetailsBlock({ entry }: { entry: AuditEntry }) {
  return (
    <div className="space-y-2">
      {entry.detailsJson && (
        <div>
          <div className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold mb-1">
            Details
          </div>
          <pre className="text-[11px] font-mono bg-card border border-border rounded px-3 py-2 overflow-x-auto">
            {prettyJson(entry.detailsJson)}
          </pre>
        </div>
      )}
      {entry.userAgent && (
        <div>
          <div className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold mb-1">
            User Agent
          </div>
          <div className="text-[11px] font-mono bg-card border border-border rounded px-3 py-2 break-all">
            {entry.userAgent}
          </div>
        </div>
      )}
    </div>
  );
}

function ActionBadge({ action }: { action: string }) {
  // Light semantic coloring — no exhaustive mapping, just a few
  // categories that operators care about at a glance.
  const cls =
    action.startsWith('LOGIN') || action.startsWith('LOGOUT') || action === 'CHANGE_PASSWORD'
      ? 'bg-sky-50 text-sky-800 border-sky-200'
      : action.startsWith('APPLY_') || action.startsWith('ROLLBACK_')
        ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
        : action === 'RESTART_ENGINE' || action.startsWith('STOP_')
          ? 'bg-red-50 text-red-800 border-red-200'
          : action.startsWith('RELOAD_') || action.startsWith('START_')
            ? 'bg-amber-50 text-amber-800 border-amber-200'
            : 'bg-muted text-foreground border-border';
  return (
    <span className={`inline-flex px-1.5 py-[1px] rounded border text-[10px] font-semibold tracking-wide ${cls}`}>
      {action}
    </span>
  );
}

function ResultBadge({ result }: { result: 'success' | 'failed' }) {
  return result === 'success' ? (
    <span className="inline-flex px-1.5 py-[1px] rounded border border-emerald-200 bg-emerald-50 text-emerald-800 text-[10px] font-semibold tracking-wide">
      SUCCESS
    </span>
  ) : (
    <span className="inline-flex px-1.5 py-[1px] rounded border border-red-200 bg-red-50 text-red-800 text-[10px] font-semibold tracking-wide">
      FAILED
    </span>
  );
}

function formatTime(ms: number): string {
  // Always UTC in the table for audit integrity. If users need local
  // time, we'll add a toggle later — mixing the two in one column is
  // worse than picking one.
  const d = new Date(ms);
  const y = d.getUTCFullYear();
  const M = String(d.getUTCMonth() + 1).padStart(2, '0');
  const D = String(d.getUTCDate()).padStart(2, '0');
  const h = String(d.getUTCHours()).padStart(2, '0');
  const m = String(d.getUTCMinutes()).padStart(2, '0');
  const s = String(d.getUTCSeconds()).padStart(2, '0');
  return `${y}-${M}-${D} ${h}:${m}:${s}`;
}

function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw; // not JSON — show as-is
  }
}
