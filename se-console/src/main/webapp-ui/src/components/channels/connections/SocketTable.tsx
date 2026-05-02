import type { ChannelSummary, SocketSummary, ActionKey } from '../../../api/channels.types';
import {
  formatLatency,
  formatDurationCompact,
  formatAgo,
  socketStatusDotClass,
  socketStatusTextClass,
  valueToneClass,
} from '../../../lib/formatEngine';
import { formatNumber } from '../../../lib/format';
import { SocketActionMenu } from './SocketActionMenu';

interface Props {
  channel: ChannelSummary;
  onSocketAction: (socket: SocketSummary, action: ActionKey) => void;
  busyHashId: string | null;   // hashId currently awaiting action response (greyed out)
}

/**
 * Per-socket table — 11 data columns matching mockup v7 + 1 Actions column.
 *
 * <pre>
 *   SOCKET | STATUS | LOCAL | REMOTE | IN-FLIGHT | ERROR |
 *   UPTIME | LAST CONN | LAST DISC | LAST ERR | LAST MSG | ⋮
 * </pre>
 *
 * Server sockets appear first, then clients, each group separated by a
 * faint divider row so operators can eyeball the layout at a glance.
 */
export function SocketTable({ channel, onSocketAction, busyHashId }: Props) {
  const rows: Array<{ socket: SocketSummary; groupLabel?: string }> = [];
  if (channel.servers.length > 0) {
    rows.push({ socket: channel.servers[0], groupLabel: 'SERVER' });
    for (let i = 1; i < channel.servers.length; i++) rows.push({ socket: channel.servers[i] });
  }
  if (channel.clients.length > 0) {
    rows.push({ socket: channel.clients[0], groupLabel: 'CLIENTS' });
    for (let i = 1; i < channel.clients.length; i++) rows.push({ socket: channel.clients[i] });
  }

  if (rows.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-border bg-muted/10 p-8 text-center text-[12px] text-muted-foreground italic">
        No sockets on this channel.
      </div>
    );
  }

  return (
    <div className="rounded-md border border-border bg-card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-[12px] font-mono border-collapse">
          <thead>
            <tr className="text-left text-[10px] uppercase tracking-wider text-muted-foreground border-b border-border">
              <Th>Socket</Th>
              <Th>Status</Th>
              <Th>Local</Th>
              <Th>Remote</Th>
              <Th className="text-right">In-flight</Th>
              <Th className="text-right">Errors</Th>
              <Th>Uptime</Th>
              <Th>Last conn</Th>
              <Th>Last disc</Th>
              <Th>Last err</Th>
              <Th>Last msg</Th>
              <Th className="text-right pr-3">{''}</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <SocketRow
                key={row.socket.hashId}
                socket={row.socket}
                groupLabel={row.groupLabel}
                busy={busyHashId === row.socket.hashId}
                onAction={(action) => onSocketAction(row.socket, action)}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------

function SocketRow({
  socket,
  groupLabel,
  busy,
  onAction,
}: {
  socket: SocketSummary;
  groupLabel?: string;
  busy: boolean;
  onAction: (action: ActionKey) => void;
}) {
  const nowMs = Date.now();

  return (
    <>
      {groupLabel && (
        <tr className="bg-muted/40 border-b border-border">
          <td
            colSpan={12}
            className="px-3 py-1 text-[10px] uppercase tracking-wider font-semibold text-muted-foreground"
          >
            {groupLabel}
          </td>
        </tr>
      )}
      <tr
        className={
          'border-b border-border last:border-b-0 hover:bg-muted/20 transition-colors ' +
          (busy ? 'opacity-60' : '')
        }
      >
        <Td className="text-foreground">
          <div className="flex items-center gap-1.5">
            <TypeBadge type={socket.type} />
            <span className="truncate max-w-[180px]" title={socket.socketId}>{socket.socketId}</span>
          </div>
        </Td>
        <Td>
          <span className="inline-flex items-center gap-1.5">
            <span className={`w-1.5 h-1.5 rounded-full ${socketStatusDotClass(socket.runtime.state)}`} />
            <span className={`text-[10px] font-semibold ${socketStatusTextClass(socket.runtime.state)}`}>
              {socket.runtime.state}
            </span>
          </span>
        </Td>
        <Td>{socket.runtime.localHost || '—'}</Td>
        <Td>{socket.runtime.remoteHost || '—'}</Td>
        <Td className={`text-right ${valueToneClass(socket.queue.depth)}`}>
          {formatNumber(socket.queue.depth)}
        </Td>
        <Td className={`text-right ${valueToneClass(socket.queue.errCount, /*warn*/ true)}`}>
          {formatNumber(socket.queue.errCount)}
          {socket.metrics.latency.maxNs > 0 && (
            <span className="ml-2 text-muted-foreground text-[10px]">
              · {formatLatency(socket.metrics.latency.avgNs)} avg
            </span>
          )}
        </Td>
        <Td>{socket.runtime.startTime > 0 ? formatDurationCompact(nowMs - socket.runtime.startTime) : '—'}</Td>
        <Td>{formatAgo(socket.runtime.lastConnect)}</Td>
        <Td>{formatAgo(socket.runtime.lastDisconnect)}</Td>
        <Td className={socket.queue.lastErr > 0 ? 'text-red-600' : ''}>
          {formatAgo(socket.queue.lastErr)}
        </Td>
        <Td>{formatAgo(socket.queue.lastMsg)}</Td>
        <Td className="text-right pr-3">
          <SocketActionMenu
            status={socket.runtime.state}
            disabled={busy}
            onAction={onAction}
          />
        </Td>
      </tr>
    </>
  );
}

function Th({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <th className={`py-2 px-3 font-semibold whitespace-nowrap ${className}`}>{children}</th>;
}

function Td({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <td className={`py-2 px-3 whitespace-nowrap ${className}`}>{children}</td>;
}

function TypeBadge({ type }: { type: 'SERVER' | 'CLIENT' }) {
  const className = type === 'SERVER'
    ? 'bg-sky-50 text-sky-700 border-sky-200'
    : 'bg-violet-50 text-violet-700 border-violet-200';
  return (
    <span className={`inline-flex items-center px-1.5 py-[1px] rounded border text-[9px] font-semibold tracking-wide ${className}`}>
      {type === 'SERVER' ? 'S' : 'C'}
    </span>
  );
}
