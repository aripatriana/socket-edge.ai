import { useParams, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { useChannelDetail } from '../../hooks/useChannelDetail';
import { channelStateBadge } from '../../lib/formatEngine';
import { MetricsTab } from './metrics/MetricsTab';
import { OverviewTab } from './overview/OverviewTab';
import { ConnectionsTab } from './connections/ConnectionsTab';

/**
 * Channel detail page shell. 3 tabs total — channel-scoped only.
 *
 * <p>Configuration and Log tabs were removed in Chat 3c-1e: config editing
 * happens at the global {@code /config} menu (covers all engine config
 * files, not just channel.conf), and log tailing happens at the global
 * {@code /logs} menu (engine log is one stream, not per-channel).
 *
 * <p>URL shape:
 *   /channels/{name}               → redirects to /channels/{name}/overview
 *   /channels/{name}/overview      → Overview tab
 *   /channels/{name}/metrics       → Metrics tab
 *   /channels/{name}/connections   → Connections tab
 */

type TabKey = 'overview' | 'metrics' | 'connections';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview',    label: 'Overview' },
  { key: 'metrics',     label: 'Metrics' },
  { key: 'connections', label: 'Connections' },
];

export function ChannelDetailPage() {
  const { name, tab } = useParams<{ name: string; tab?: TabKey }>();
  const navigate = useNavigate();
  const location = useLocation();

  if (!tab) {
    return <Navigate to={`/channels/${name}/overview`} replace />;
  }
  // Redirect unknown tab keys (old /configuration, /log URLs) to overview.
  if (!TABS.some((t) => t.key === tab)) {
    return <Navigate to={`/channels/${name}/overview`} replace />;
  }

  const { data: channel, isLoading, isError, error } = useChannelDetail(name);
  const lastUpdated = channel ? new Date() : null;
  const activeTab = tab as TabKey;

  return (
    <AppShell lastUpdated={lastUpdated}>
      {/* Persistent header */}
      <header className="mb-3">
        <div className="flex items-center gap-3 flex-wrap">
          <button
            type="button"
            onClick={() => navigate('/channels')}
            className="text-[12px] text-muted-foreground hover:text-foreground transition-colors"
          >
            Engine / Channels
          </button>
          <span className="text-[12px] text-muted-foreground">/</span>
          <span className="font-mono font-semibold text-[15px] text-foreground">
            {name}
          </span>

          {channel && (() => {
            const badge = channelStateBadge(channel.aggregateState);
            return (
              <span className={`inline-flex items-center px-2 py-0.5 rounded border text-[10px] font-semibold tracking-wide ${badge.className}`}>
                {badge.label}
              </span>
            );
          })()}

          {channel && (
            <span className="text-[12px] text-muted-foreground">
              {channel.socketsUp}/{channel.socketsTotal} sockets up
            </span>
          )}

          <div className="flex-1" />
        </div>
      </header>

      {/* Tab bar */}
      <nav role="tablist" className="flex items-center gap-0 border-b border-border mb-4">
        {TABS.map((t) => {
          const isActive = t.key === activeTab;
          return (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => navigate(`/channels/${name}/${t.key}`)}
              className={[
                'relative px-4 py-2 text-[13px] transition-colors',
                isActive
                  ? 'text-foreground font-semibold'
                  : 'text-muted-foreground hover:text-foreground',
              ].join(' ')}
            >
              {t.label}
              {isActive && (
                <span className="absolute bottom-[-1px] left-0 right-0 h-[2px] bg-primary" />
              )}
            </button>
          );
        })}
      </nav>

      {/* Tab body */}
      {isError && (
        <ErrorPanel
          message={error instanceof Error ? error.message : 'Failed to load channel'}
        />
      )}

      {!isError && isLoading && !channel && (
        <div className="flex items-center justify-center h-[200px] text-muted-foreground">
          Loading {name}…
        </div>
      )}

      {channel && activeTab === 'overview' && (
        <OverviewTab channelName={name!} channel={channel} />
      )}

      {channel && activeTab === 'metrics' && (
        <MetricsTab channelName={name!} channel={channel} />
      )}

      {channel && activeTab === 'connections' && (
        <ConnectionsTab channelName={name!} channel={channel} />
      )}

      <span className="hidden" data-location={location.pathname} />
    </AppShell>
  );
}

function ErrorPanel({ message }: { message: string }) {
  return (
    <div className="rounded-md border border-destructive bg-destructive/10 p-4">
      <div className="text-[13px] font-semibold text-destructive">
        Failed to load channel
      </div>
      <div className="text-[11px] text-muted-foreground mt-1 font-mono">{message}</div>
    </div>
  );
}
