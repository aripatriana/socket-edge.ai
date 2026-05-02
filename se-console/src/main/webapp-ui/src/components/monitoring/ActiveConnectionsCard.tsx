import { useMemo, useState } from 'react';
import { Card } from '../dashboard/Card';
import { useNetworkConnections } from '../../hooks/useNetworkMetrics';
import type { TcpConnection } from '../../api/network.types';
import { formatNumber } from '../../lib/format';

type SortKey = 'local' | 'remote' | 'state' | 'pid';
type SortDir = 'asc' | 'desc';

/**
 * Active TCP connections table. Shows up to 1000 connections per server
 * cap — if truncated, a banner indicates total vs shown.
 *
 * Features:
 *  - Search by local or remote address/port
 *  - State filter dropdown
 *  - Sortable columns
 *  - Threshold-based color coding (TIME_WAIT amber, CLOSE_WAIT red, etc.)
 */
export function ActiveConnectionsCard() {
  const query = useNetworkConnections();
  const [search, setSearch] = useState('');
  const [stateFilter, setStateFilter] = useState<string>('');
  const [sortKey, setSortKey] = useState<SortKey>('local');
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const data = query.data;
  const connections = data?.connections ?? [];

  // Unique states for filter dropdown, preserving order of first appearance.
  const uniqueStates = useMemo(() => {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const c of connections) {
      if (!seen.has(c.state)) {
        seen.add(c.state);
        out.push(c.state);
      }
    }
    return out.sort();
  }, [connections]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const narrowed = connections.filter((c) => {
      if (stateFilter && c.state !== stateFilter) return false;
      if (q) {
        const hay = `${c.localAddress}:${c.localPort} ${c.remoteAddress}:${c.remotePort}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
    return sortConnections(narrowed, sortKey, sortDir);
  }, [connections, search, stateFilter, sortKey, sortDir]);

  function handleSortClick(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  }

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-center justify-between gap-3 mb-3 flex-wrap">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Active Connections
          </div>
          <div className="text-[11px] text-muted-foreground">
            {data ? (
              <>
                {filtered.length === connections.length
                  ? formatNumber(connections.length)
                  : `${formatNumber(filtered.length)} of ${formatNumber(connections.length)}`}
                {data.truncated && (
                  <span className="ml-2 text-amber-600">
                    (truncated from {formatNumber(data.totalCount)} total)
                  </span>
                )}
              </>
            ) : (
              'Loading…'
            )}
          </div>
        </div>

        <div className="flex items-center gap-3">
          <select
            value={stateFilter}
            onChange={(e) => setStateFilter(e.target.value)}
            className="text-[12px] px-2 py-1 rounded border border-border bg-background text-foreground focus:outline-none focus:border-primary cursor-pointer"
          >
            <option value="">All states</option>
            {uniqueStates.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
          <input
            type="text"
            placeholder="Search address:port…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="text-[12px] px-2.5 py-1 rounded border border-border bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary w-52"
          />
        </div>
      </div>

      {query.isLoading ? (
        <div className="flex items-center justify-center h-24 text-[12px] text-muted-foreground">
          Loading connections…
        </div>
      ) : query.isError ? (
        <div className="px-4 py-3 rounded text-[12px] bg-destructive/10 text-destructive border border-destructive">
          Failed to load connections. Retrying…
        </div>
      ) : filtered.length === 0 ? (
        <div className="flex items-center justify-center h-24 text-[12px] text-muted-foreground">
          {search.trim() || stateFilter ? 'No connections match the filter' : 'No connections'}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-[12px]">
            <thead>
              <tr className="border-b border-border text-[10px] uppercase tracking-wider text-muted-foreground">
                <ColHeader
                  label="Local"
                  sortKey="local"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                />
                <ColHeader
                  label="Remote"
                  sortKey="remote"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                />
                <ColHeader
                  label="State"
                  sortKey="state"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                  width="w-32"
                />
                <th className="text-left font-semibold py-2 px-2 w-16">
                  <span className="uppercase tracking-wider font-semibold text-muted-foreground">
                    Proto
                  </span>
                </th>
                <ColHeader
                  label="PID"
                  sortKey="pid"
                  currentSort={sortKey}
                  currentDir={sortDir}
                  onClick={handleSortClick}
                  width="w-20"
                  align="right"
                />
              </tr>
            </thead>
            <tbody>
              {filtered.map((c, i) => (
                <ConnectionRow key={`${i}-${c.localPort}-${c.remotePort}`} conn={c} />
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
  sortKey,
  currentSort,
  currentDir,
  onClick,
  width,
  align = 'left',
}: {
  label: string;
  sortKey: SortKey;
  currentSort: SortKey;
  currentDir: SortDir;
  onClick: (key: SortKey) => void;
  width?: string;
  align?: 'left' | 'right';
}) {
  const active = currentSort === sortKey;
  return (
    <th className={`${align === 'right' ? 'text-right' : 'text-left'} font-semibold py-2 px-2 ${width ?? ''}`}>
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

function ConnectionRow({ conn }: { conn: TcpConnection }) {
  const stateColor = getStateColor(conn.state);
  return (
    <tr className="border-b border-border hover:bg-muted/50">
      <td className="py-1.5 px-2 font-mono text-foreground tabular-nums">
        {conn.localAddress}:{conn.localPort}
      </td>
      <td className="py-1.5 px-2 font-mono text-foreground tabular-nums">
        {conn.remoteAddress && conn.remotePort !== 0
          ? `${conn.remoteAddress}:${conn.remotePort}`
          : <span className="text-muted-foreground">—</span>}
      </td>
      <td className={`py-1.5 px-2 font-mono text-[11px] ${stateColor}`}>{conn.state}</td>
      <td className="py-1.5 px-2 font-mono text-[11px] text-muted-foreground">{conn.protocol}</td>
      <td className="py-1.5 px-2 font-mono text-right text-muted-foreground tabular-nums">
        {conn.pid ?? '—'}
      </td>
    </tr>
  );
}

function getStateColor(state: string): string {
  switch (state) {
    case 'ESTABLISHED':
      return 'text-emerald-600';
    case 'CLOSE_WAIT':
      return 'text-destructive font-semibold';
    case 'TIME_WAIT':
      return 'text-amber-600';
    case 'LISTEN':
      return 'text-accent';
    case 'SYN_SENT':
    case 'SYN_RECV':
      return 'text-muted-foreground';
    default:
      return 'text-muted-foreground';
  }
}

function sortConnections(
  conns: TcpConnection[],
  key: SortKey,
  dir: SortDir
): TcpConnection[] {
  const sign = dir === 'asc' ? 1 : -1;
  return [...conns].sort((a, b) => {
    switch (key) {
      case 'local':
        return sign * (a.localPort - b.localPort || a.localAddress.localeCompare(b.localAddress));
      case 'remote':
        return sign * (a.remotePort - b.remotePort || a.remoteAddress.localeCompare(b.remoteAddress));
      case 'state':
        return sign * a.state.localeCompare(b.state);
      case 'pid': {
        const av = a.pid ?? -1;
        const bv = b.pid ?? -1;
        return sign * (av - bv);
      }
      default:
        return 0;
    }
  });
}
