import type { ChannelSummary } from '../../../api/channels.types';
import { useChannelConfig } from '../../../hooks/useChannelConfig';
import { ChannelConfigCard } from '../../../components/channels/overview/ChannelConfigCard';
import { RuntimeStateCard } from '../../../components/channels/overview/RuntimeStateCard';
import { PoolMembersTable } from '../../../components/channels/overview/PoolMembersTable';

interface Props {
  channelName: string;
  channel: ChannelSummary;
}

/**
 * Overview tab layout.
 *
 * <p>Config + Runtime cards sit side-by-side on wide screens; Pool members
 * table spans the full width below. On narrow screens everything stacks.
 *
 * <p>The Config card depends on the new {@code /api/channels/{name}/config}
 * endpoint. While the config loads (or if the backend hasn't been updated
 * yet and returns 404), Runtime + Pool remain renderable from data we
 * already have in {@link ChannelSummary}.
 */
export function OverviewTab({ channelName, channel }: Props) {
  const { data: config, isLoading, isError, error } = useChannelConfig(channelName);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {/* Config card */}
      {config ? (
        <ChannelConfigCard config={config} />
      ) : (
        <ConfigCardPlaceholder
          isLoading={isLoading}
          isError={isError}
          error={error}
        />
      )}

      {/* Runtime state — from ChannelSummary, always available */}
      <RuntimeStateCard channel={channel} />

      {/* Pool members — needs config. Renders placeholder while loading. */}
      {config ? (
        <PoolMembersTable config={config} channel={channel} />
      ) : (
        <PoolMembersPlaceholder
          isLoading={isLoading}
          isError={isError}
          error={error}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------

function ConfigCardPlaceholder({
  isLoading,
  isError,
  error,
}: {
  isLoading: boolean;
  isError: boolean;
  error: unknown;
}) {
  return (
    <section className="rounded-md border border-border bg-card p-4">
      <header className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground mb-3">
        CONFIGURATION
      </header>
      <Placeholder isLoading={isLoading} isError={isError} error={error} />
    </section>
  );
}

function PoolMembersPlaceholder({
  isLoading,
  isError,
  error,
}: {
  isLoading: boolean;
  isError: boolean;
  error: unknown;
}) {
  return (
    <section className="rounded-md border border-border bg-card p-4 lg:col-span-2">
      <header className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground mb-3">
        POOL MEMBERS
      </header>
      <Placeholder isLoading={isLoading} isError={isError} error={error} />
    </section>
  );
}

function Placeholder({
  isLoading,
  isError,
  error,
}: {
  isLoading: boolean;
  isError: boolean;
  error: unknown;
}) {
  if (isLoading) {
    return (
      <div className="text-[12px] text-muted-foreground italic">
        Loading configuration…
      </div>
    );
  }
  if (isError) {
    const msg = error instanceof Error ? error.message : 'unknown error';
    const hint = msg.includes('404')
      ? 'Endpoint /api/channels/{name}/config is not yet implemented on the backend. See api-spec/README.md.'
      : msg;
    return (
      <div className="text-[12px] text-amber-700">
        Config unavailable: <span className="font-mono">{hint}</span>
      </div>
    );
  }
  return (
    <div className="text-[12px] text-muted-foreground italic">
      No configuration data.
    </div>
  );
}
