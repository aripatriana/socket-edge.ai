import { useMemo, useState } from 'react';
import type { UseQueryResult } from '@tanstack/react-query';
import { Card } from '../dashboard/Card';
import type { ThreadList as ThreadListData, ThreadSummary } from '../../api/jvm.types';
import { formatCpuTime, formatNumber } from '../../lib/format';

/**
 * Thread list with search, daemon filter, sortable columns, per-row expand.
 * Caller must supply a `useThreadsHook` returning UseQueryResult<ThreadListData>.
 */

type SortKey = 'name' | 'state' | 'cpu' | 'daemon' | 'stack';
type SortDir = 'asc' | 'desc';

export type ThreadsHook = () => UseQueryResult<ThreadListData>;

export function ThreadList({ useThreadsHook }: { useThreadsHook: ThreadsHook }) {
  const query = useThreadsHook();
  const [search, setSearch] = useState('');
  const [excludeDaemons, setExcludeDaemons] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // Default: CPU descending (hottest threads first).
  const [sortKey, setSortKey] = useState<SortKey>('cpu');
  const [sortDir, setSortDir] = useState<SortDir>('desc');

  const threads = query.data?.threads ?? [];

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const narrowed = threads.filter((t) => {
      if (excludeDaemons && t.daemon) return false;
      if (q && !t.name.toLowerCase().includes(q)) return false;
      return true;
    });
    return sortThreads(narrowed, sortKey, sortDir);
  }, [threads, search, excludeDaemons, sortKey, sortDir]);

  function handleSortClick(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      // CPU defaults to desc (hot threads on top); others to asc.
      setSortDir(key === 'cpu' ? 'desc' : 'asc');
    }
  }

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-center justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Threads
          </div>
          <div className="text-[11px] text-muted-foreground">
            {filtered.length === threads.length
              ? `${formatNumber(threads.length)} total`
              : `${filtered.length} of ${formatNumber(threads.length)} total`}
          </div>
        </div>

        <div className="flex items-center gap-3">
          <label className="flex items-center gap-1.5 text-[12px] text-muted-foreground cursor-pointer">
            <input
              type="checkbox"
              checked={excludeDaemons}
              onChange={(e) => setExcludeDaemons(e.target.checked)}
              className="w-3.5 h-3.5 accent-primary cursor-pointer"
            />
            Non-daemon only
          </label>
          <input
            type="text"
            placeholder="Search by name…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="text-[12px] px-2.5 py-1 rounded border border-border bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary w-48"
          />
        </div>
      </div>

      {query.isLoading ? (
        <div className="flex items-center justify-center h-24 text-[12px] text-muted-foreground">
          Loading threads…
        </div>
      ) : query.isError ? (
        <div className="px-4 py-3 rounded text-[12px] bg-destructive/10 text-destructive border border-destructive">
          Failed to load thread list. Retrying…
        </div>
      ) : filtered.length === 0 ? (
        <div className="flex items-center justify-center h-24 text-[12px] text-muted-foreground">
          {search.trim() ? `No threads match "${search}"` : 'No threads'}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-[12px]">
            <thead>
              <tr className="border-b border-border text-[10px] uppercase tracking-wider text-muted-foreground">
                <ColHeader
                  label="Name"
                  align="left"
                  sortKey="name"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                />
                <ColHeader
                  label="State"
                  align="left"
                  sortKey="state"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                  width="w-28"
                />
                <ColHeader
                  label="CPU"
                  align="right"
                  sortKey="cpu"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                  width="w-24"
                />
                <ColHeader
                  label="Daemon"
                  align="center"
                  sortKey="daemon"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                  width="w-16"
                />
                <ColHeader
                  label="Stack"
                  align="right"
                  sortKey="stack"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                  width="w-14"
                />
              </tr>
            </thead>
            <tbody>
              {filtered.map((t) => (
                <ThreadRow
                  key={t.id}
                  thread={t}
                  expanded={expandedId === t.id}
                  onToggle={() => setExpandedId(expandedId === t.id ? null : t.id)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

function ColHeader({
  label,
  align,
  sortKey,
  currentSort,
  currentDir,
  onClick,
  width,
}: {
  label: string;
  align: 'left' | 'right' | 'center';
  sortKey: SortKey;
  currentSort: SortKey;
  currentDir: SortDir;
  onClick: (key: SortKey) => void;
  width?: string;
}) {
  const active = currentSort === sortKey;
  const alignClass =
    align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left';

  return (
    <th className={`${alignClass} font-semibold py-2 px-2 ${width ?? ''}`}>
      <button
        type="button"
        onClick={() => onClick(sortKey)}
        className={[
          'inline-flex items-center gap-1 uppercase tracking-wider font-semibold cursor-pointer',
          'hover:text-foreground transition-colors',
          active ? 'text-foreground' : 'text-muted-foreground',
          align === 'right' ? 'flex-row-reverse' : '',
        ].join(' ')}
      >
        <span>{label}</span>
        <span className="text-[8px] opacity-70" aria-hidden="true">
          {active ? (currentDir === 'asc' ? '▲' : '▼') : '↕'}
        </span>
      </button>
    </th>
  );
}

function ThreadRow({
  thread,
  expanded,
  onToggle,
}: {
  thread: ThreadSummary;
  expanded: boolean;
  onToggle: () => void;
}) {
  const stateClass = stateColor(thread.state);

  return (
    <>
      <tr
        className="border-b border-border hover:bg-muted/50 cursor-pointer"
        onClick={onToggle}
      >
        <td className="py-1.5 px-2 font-mono text-foreground truncate max-w-0">
          <div className="flex items-center gap-1.5 min-w-0">
            <span
              className="text-[10px] text-muted-foreground w-3 flex-shrink-0"
              style={{ transform: expanded ? 'rotate(90deg)' : 'rotate(0)' }}
            >
              ▸
            </span>
            <span className="truncate" title={thread.name}>
              {thread.name}
            </span>
            {thread.lockOwnerName && (
              <span
                className="text-[10px] text-destructive flex-shrink-0"
                title={`Waiting for lock held by "${thread.lockOwnerName}"`}
              >
                ↯
              </span>
            )}
          </div>
        </td>
        <td className={`py-1.5 px-2 font-mono text-[11px] ${stateClass}`}>
          {thread.state}
        </td>
        <td className="py-1.5 px-2 font-mono text-right text-foreground tabular-nums">
          {formatCpuTime(thread.cpuTimeNs)}
        </td>
        <td className="py-1.5 px-2 text-center text-muted-foreground">
          {thread.daemon ? '✓' : '—'}
        </td>
        <td className="py-1.5 px-2 font-mono text-right text-muted-foreground tabular-nums">
          {thread.stackDepth}
        </td>
      </tr>
      {expanded && (
        <tr className="border-b border-border bg-muted/30">
          <td colSpan={5} className="py-3 px-8">
            {thread.lockName && (
              <div className="text-[11px] text-muted-foreground mb-2">
                Waiting on:{' '}
                <span className="font-mono text-foreground">{thread.lockName}</span>
                {thread.lockOwnerName && (
                  <>
                    {' '}· held by{' '}
                    <span className="font-mono text-foreground">
                      "{thread.lockOwnerName}" #{thread.lockOwnerId}
                    </span>
                  </>
                )}
              </div>
            )}
            {thread.topFrames.length > 0 ? (
              <pre className="font-mono text-[10.5px] text-foreground bg-background rounded px-3 py-2 border border-border overflow-x-auto leading-relaxed">
                {thread.topFrames.map((frame) => `\tat ${frame}\n`).join('')}
                {thread.stackDepth > thread.topFrames.length && (
                  <span className="text-muted-foreground">
                    … {thread.stackDepth - thread.topFrames.length} more frames
                  </span>
                )}
              </pre>
            ) : (
              <div className="text-[11px] text-muted-foreground italic">
                No stack trace available
              </div>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

function stateColor(state: string): string {
  switch (state) {
    case 'RUNNABLE':
      return 'text-emerald-600';
    case 'BLOCKED':
      return 'text-destructive font-semibold';
    case 'WAITING':
    case 'TIMED_WAITING':
      return 'text-muted-foreground';
    case 'TERMINATED':
      return 'text-muted-foreground italic';
    default:
      return 'text-foreground';
  }
}

/**
 * Sort threads by the given key + direction.
 * Null values for CPU time are pushed to the end (stable across both dirs
 * so they don't flip around awkwardly).
 */
function sortThreads(
  threads: ThreadSummary[],
  key: SortKey,
  dir: SortDir
): ThreadSummary[] {
  const sign = dir === 'asc' ? 1 : -1;
  return [...threads].sort((a, b) => {
    switch (key) {
      case 'name':
        return sign * a.name.localeCompare(b.name);
      case 'state':
        return sign * a.state.localeCompare(b.state) || a.name.localeCompare(b.name);
      case 'cpu': {
        const av = a.cpuTimeNs;
        const bv = b.cpuTimeNs;
        // Nulls always at bottom regardless of direction.
        if (av == null && bv == null) return a.name.localeCompare(b.name);
        if (av == null) return 1;
        if (bv == null) return -1;
        return sign * (av - bv);
      }
      case 'daemon':
        return sign * (Number(a.daemon) - Number(b.daemon)) || a.name.localeCompare(b.name);
      case 'stack':
        return sign * (a.stackDepth - b.stackDepth) || a.name.localeCompare(b.name);
      default:
        return 0;
    }
  });
}
