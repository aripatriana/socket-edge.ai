import { useCallback, useMemo, useState } from 'react';
import { Search, X } from 'lucide-react';
import { AppShell } from '../../components/layout/AppShell';
import { ChannelCard } from '../../components/channels/ChannelCard';
import { useChannels } from '../../hooks/useChannels';
import { formatNumber } from '../../lib/format';
import type { ChannelSummary } from '../../api/channels.types';

/**
 * Channels list page. Pulls merged telemetry from `/api/channels` every 2s.
 *
 * Layout:
 *   ┌─ Title row: "Channels"  summary text  [stale chip?] ─────────────────┐
 *   ├─ Toolbar: [search]  [Expand all] [Collapse all] ────────────────────┤
 *   ├─ Stack of ChannelCards ──────────────────────────────────────────────┤
 *   └──────────────────────────────────────────────────────────────────────┘
 */
export function ChannelsPage() {
  const { data, isLoading, isError, error } = useChannels();
  const channels = data?.channels ?? [];
  const lastUpdated = data?.lastUpdateMillis ? new Date(data.lastUpdateMillis) : null;

  const [search, setSearch] = useState('');
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return channels;
    return channels.filter((c) => c.name.toLowerCase().includes(q));
  }, [channels, search]);

  const totals = useMemo(() => summarise(channels), [channels]);

  const toggleCard = useCallback((name: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  }, []);

  const expandAll = useCallback(
    () => setExpanded(new Set(filtered.map((c) => c.name))),
    [filtered],
  );
  const collapseAll = useCallback(() => setExpanded(new Set()), []);

  return (
    <AppShell lastUpdated={lastUpdated}>
      {/* Title row */}
      <header className="flex items-baseline gap-3 flex-wrap mb-4">
        <h1 className="text-[18px] font-semibold text-foreground">Channels</h1>
        <span className="text-[13px] font-mono text-muted-foreground">
          {isLoading && !data ? (
            'loading…'
          ) : (
            <>
              {formatNumber(totals.channelCount)} channel
              {totals.channelCount === 1 ? '' : 's'} ·{' '}
              <span className="text-foreground">{formatNumber(totals.tps)} TPS</span>
              {' · '}
              {formatNumber(totals.up)}/{formatNumber(totals.total)} sockets up
            </>
          )}
        </span>
        {data && !data.reachable && (
          <span
            className="text-[11px] px-2 py-0.5 rounded border border-red-200 bg-red-50 text-red-700"
            title={data.lastError ?? 'Engine unreachable'}
          >
            stale · engine unreachable
          </span>
        )}
      </header>

      {/* Toolbar */}
      <div className="flex items-center gap-2 flex-wrap mb-3">
        <SearchBox value={search} onChange={setSearch} />
        <div className="flex-1" />
        <TextButton onClick={expandAll} disabled={filtered.length === 0}>
          Expand all
        </TextButton>
        <TextButton onClick={collapseAll} disabled={expanded.size === 0}>
          Collapse all
        </TextButton>
      </div>

      {/* Cards */}
      <section className="flex flex-col gap-2">
        {isError && (
          <ErrorPanel
            message={error instanceof Error ? error.message : 'Failed to load channels'}
          />
        )}

        {!isError && !isLoading && filtered.length === 0 && (
          <EmptyPanel search={search} hasAny={channels.length > 0} />
        )}

        {filtered.map((c) => (
          <ChannelCard
            key={c.name}
            channel={c}
            expanded={expanded.has(c.name)}
            onToggle={toggleCard}
          />
        ))}
      </section>
    </AppShell>
  );
}

// ===========================================================================
// Helpers
// ===========================================================================

function summarise(channels: ChannelSummary[]) {
  let tps = 0, up = 0, total = 0;
  for (const c of channels) {
    tps += c.aggregate.throughputTps.totalAvg;
    up += c.socketsUp;
    total += c.socketsTotal;
  }
  return { channelCount: channels.length, tps, up, total };
}

function SearchBox({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <div className="relative w-64">
      <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Filter by channel name…"
        className="w-full pl-8 pr-8 py-1.5 text-[12px] font-mono rounded border border-border bg-card focus:outline-none focus:ring-1 focus:ring-accent focus:border-accent"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          aria-label="Clear search"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
}

function TextButton({
  onClick,
  disabled,
  children,
}: {
  onClick: () => void;
  disabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="px-2.5 py-1 text-[12px] rounded border border-border bg-card hover:bg-muted/40 disabled:cursor-not-allowed disabled:opacity-50 transition-colors"
    >
      {children}
    </button>
  );
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="rounded-md border border-destructive bg-destructive/10 p-4">
      <div className="text-[13px] font-semibold text-destructive">Failed to load channels</div>
      <div className="text-[11px] text-muted-foreground mt-1 font-mono">{message}</div>
    </div>
  );
}

function EmptyPanel({ search, hasAny }: { search: string; hasAny: boolean }) {
  return (
    <div className="rounded-md border border-dashed border-border bg-muted/20 p-8 text-center">
      <div className="text-[13px] text-foreground">
        {search
          ? `No channels match "${search}".`
          : hasAny
            ? 'No channels to show.'
            : 'No channels configured on the engine yet.'}
      </div>
      {!search && !hasAny && (
        <div className="text-[11px] text-muted-foreground mt-1">
          Add a channel in channel.conf on the engine, then reload.
        </div>
      )}
    </div>
  );
}
