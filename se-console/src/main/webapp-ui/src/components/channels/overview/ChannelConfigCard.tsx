import type { ChannelConfigResponse } from '../../../api/channels.types';

/**
 * Configuration card — the semi-static parameters from channel.conf.
 * Key-value grid, monospace for values, compact padding.
 *
 * <p>Renders a best-effort view even when some fields are missing: a
 * server-only channel (no `client` block) still shows listen info and
 * server strategy.
 */
export function ChannelConfigCard({ config }: { config: ChannelConfigResponse }) {
  const poolCount = poolSize(config);

  return (
    <Card title="CONFIGURATION">
      <dl className="grid grid-cols-[120px_1fr] gap-x-4 gap-y-2.5 text-[12px] font-mono">
        <Row label="id"           value={config.name} />
        <Row label="type"         value={config.type || '—'} />

        <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
          profiles
        </dt>
        <dd>
          <ProfileChips profiles={config.profiles} />
        </dd>

        <Row label="unknown-mti"  value={config.unknownMti || '—'} />

        {config.server && (
          <>
            <Row
              label="listen"
              value={`${config.server.listenHost || '0.0.0.0'}:${config.server.listenPort}`}
            />
            <Row label="server strategy" value={config.server.strategy || '—'} />
          </>
        )}

        {config.client && (
          <Row label="client strategy" value={config.client.strategy || '—'} />
        )}

        {poolCount > 0 && (
          <Row
            label="pool"
            value={`${poolCount} ${poolCount === 1 ? 'member' : 'members'} (see below)`}
          />
        )}
      </dl>
    </Card>
  );
}

function ProfileChips({ profiles }: { profiles: string[] | null | undefined }) {
  if (!profiles || profiles.length === 0) {
    return <span className="text-muted-foreground">—</span>;
  }
  return (
    <div className="flex flex-wrap gap-1">
      {profiles.map((p) => (
        <span
          key={p}
          className="inline-flex items-center px-1.5 py-0.5 rounded border text-[10px] font-semibold tracking-wide bg-sky-50 text-sky-700 border-sky-200"
        >
          {p}
        </span>
      ))}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
        {label}
      </dt>
      <dd className="text-foreground truncate">{value}</dd>
    </>
  );
}

function poolSize(config: ChannelConfigResponse): number {
  const serverPool = config.server?.pool?.length ?? 0;
  const clientPool = config.client?.endpoints?.length ?? 0;
  return serverPool + clientPool;
}

// Compact card shell shared by the three Overview cards.
function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-md border border-border bg-card p-4">
      <header className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground mb-3">
        {title}
      </header>
      {children}
    </section>
  );
}
