import type {
  ChannelConfigResponse,
  ChannelSummary,
  PoolEndpoint,
  SocketStatus,
} from '../../../api/channels.types';
import { socketStatusDotClass, socketStatusTextClass } from '../../../lib/formatEngine';

/**
 * Pool members table.
 *
 * <p>Rows combine engine config endpoints (server pool + client endpoints)
 * with live socket state. The runtime status is derived on the frontend
 * by matching the config's {@code host:port} against live socket
 * {@code remoteHost} — no extra backend work required.
 */
export function PoolMembersTable({
  config,
  channel,
}: {
  config: ChannelConfigResponse;
  channel: ChannelSummary;
}) {
  const rows = buildRows(config, channel);

  return (
    <section className="rounded-md border border-border bg-card p-4 lg:col-span-2">
      <header className="flex items-baseline justify-between mb-3">
        <span className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground">
          POOL MEMBERS
        </span>
        <span className="text-[11px] text-muted-foreground font-mono">
          {rows.length} {rows.length === 1 ? 'member' : 'members'}
        </span>
      </header>

      {rows.length === 0 ? (
        <div className="text-[12px] italic text-muted-foreground">
          No pool members configured.
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-[12px] font-mono border-collapse">
            <thead>
              <tr className="text-left text-[10px] uppercase tracking-wider text-muted-foreground border-b border-border">
                <Th>Direction</Th>
                <Th>Endpoint</Th>
                <Th className="text-right">Weight</Th>
                <Th className="text-right">Priority</Th>
                <Th className="text-right">Max fails</Th>
                <Th className="text-right">Fail timeout</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr
                  key={`${r.direction}:${r.endpoint.host}:${r.endpoint.port}`}
                  className="border-b border-border last:border-b-0 hover:bg-muted/30 transition-colors"
                >
                  <Td>
                    <DirectionChip direction={r.direction} />
                  </Td>
                  <Td className="text-foreground">
                    {r.endpoint.host}
                    <span className="text-muted-foreground">:</span>
                    {r.endpoint.port}
                  </Td>
                  <Td className="text-right">{r.endpoint.weight}</Td>
                  <Td className="text-right">{r.endpoint.priority}</Td>
                  <Td className="text-right">{r.endpoint.maxfails}</Td>
                  <Td className="text-right">{r.endpoint.failTimeout}s</Td>
                  <Td>
                    <StatusCell status={r.runtimeStatus} />
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// Row building
// ---------------------------------------------------------------------------

type Direction = 'server-pool' | 'client';

interface PoolRow {
  direction: Direction;
  endpoint: PoolEndpoint;
  runtimeStatus: SocketStatus | null;   // null = no matching live socket
}

function buildRows(config: ChannelConfigResponse, channel: ChannelSummary): PoolRow[] {
  const rows: PoolRow[] = [];

  // Server pool rows: status comes directly from the server socket state
  // (same source as jsocket.sh status → SocketTelemetry.getRuntimeState().status).
  // remoteHost matching is wrong here because server sockets show the peer's
  // ephemeral port, not the configured listen port.
  const serverState = channel.servers[0]?.runtime.state ?? null;

  // Client rows: primary match by remoteHost (works when ACTIVE/connected).
  // Fallback to positional match for sockets in WAIT/DOWN state whose remoteHost is blank.
  const liveClientByRemote = new Map<string, SocketStatus>();
  for (const s of channel.clients) {
    const key = hostPortKey(s.runtime.remoteHost);
    if (key && key !== '-') liveClientByRemote.set(key, s.runtime.state);
  }
  const unmatchedClients = channel.clients.filter(s => {
    const key = hostPortKey(s.runtime.remoteHost);
    return !key || key === '-' || !liveClientByRemote.has(key);
  });
  let unmatchedIdx = 0;

  if (config.server?.pool) {
    for (const ep of config.server.pool) {
      rows.push({
        direction: 'server-pool',
        endpoint: ep,
        runtimeStatus: serverState,
      });
    }
  }

  if (config.client?.endpoints) {
    for (const ep of config.client.endpoints) {
      const key = `${ep.host}:${ep.port}`;
      const status = liveClientByRemote.get(key)
        ?? unmatchedClients[unmatchedIdx++]?.runtime.state
        ?? null;
      rows.push({
        direction: 'client',
        endpoint: ep,
        runtimeStatus: status,
      });
    }
  }

  return rows;
}

/** "10.0.1.50:51200" → "10.0.1.50:51200"; strips stray whitespace / ports on hostless addrs. */
function hostPortKey(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;
  // Already host:port — use as-is. Engine's remoteHost is formatted this way.
  return trimmed;
}

// ---------------------------------------------------------------------------
// Cell sub-components
// ---------------------------------------------------------------------------

function Th({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <th className={`py-2 pr-3 font-semibold ${className}`}>{children}</th>;
}

function Td({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <td className={`py-2 pr-3 ${className}`}>{children}</td>;
}

function DirectionChip({ direction }: { direction: Direction }) {
  const label = direction === 'server-pool' ? 'server pool' : 'client';
  const className = direction === 'server-pool'
    ? 'bg-sky-50 text-sky-700 border-sky-200'
    : 'bg-violet-50 text-violet-700 border-violet-200';
  return (
    <span
      className={`inline-flex items-center px-1.5 py-0.5 rounded border text-[10px] font-semibold tracking-wide ${className}`}
    >
      {label}
    </span>
  );
}

function StatusCell({ status }: { status: SocketStatus | null }) {
  if (!status) {
    return (
      <span className="inline-flex items-center gap-1.5 text-muted-foreground">
        <span className="w-1.5 h-1.5 rounded-full bg-slate-300" />
        <span>—</span>
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={`w-1.5 h-1.5 rounded-full ${socketStatusDotClass(status)}`} />
      <span className={`text-[10px] font-semibold ${socketStatusTextClass(status)}`}>
        {status}
      </span>
    </span>
  );
}
