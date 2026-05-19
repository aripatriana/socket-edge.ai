import { useNavigate } from 'react-router-dom';
import { ChevronDown, ChevronRight, Server, Radio } from 'lucide-react';
import type { ChannelSummary, SocketSummary } from '../../api/channels.types';
import {
  formatLatency,
  formatDurationCompact,
  formatAgo,
  channelStateBadge,
  socketStatusTextClass,
  socketStatusDotClass,
  valueToneClass,
} from '../../lib/formatEngine';
import { formatNumber } from '../../lib/format';

interface Props {
  channel: ChannelSummary;
  expanded: boolean;
  onToggle: (name: string) => void;
}

/**
 * Collapsible channel card. Chat 3c-1b: the "Details →" button now
 * navigates to the new detail page. Start / Stop / Restart remain
 * disabled pending the Connections tab in Chat 3c-1c.
 */
export function ChannelCard({ channel, expanded, onToggle }: Props) {
  const navigate = useNavigate();
  const badge = channelStateBadge(channel.aggregateState);
  const Chevron = expanded ? ChevronDown : ChevronRight;

  const openDetails = () => navigate(`/channels/${encodeURIComponent(channel.name)}/overview`);

  return (
    <div className="rounded-md border border-border bg-card overflow-hidden">
      {/* ============================== Header ============================== */}
      <button
        type="button"
        onClick={() => onToggle(channel.name)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-muted/40 transition-colors"
        aria-expanded={expanded}
      >
        <Chevron className="w-4 h-4 text-muted-foreground shrink-0" />
        <span className={`inline-flex items-center px-2 py-0.5 rounded border text-[10px] font-semibold tracking-wide ${badge.className}`}>
          {badge.label}
        </span>
        <span className="font-mono font-semibold text-[13px] text-foreground">{channel.name}</span>
        <span className="text-[12px] text-muted-foreground">
          {channel.socketsUp}/{channel.socketsTotal} sockets up
        </span>
        <div className="flex-1" />
        <div className="hidden md:flex items-center gap-5 font-mono text-[12px]">
          <Kpi label="TPS"       value={formatNumber(channel.aggregate.throughputTps.totalAvg)} tone={valueToneClass(channel.aggregate.throughputTps.totalAvg)} />
          <Kpi label="avg"       value={formatLatency(channel.aggregate.latency.maxAvg)}      tone="text-foreground" />
          <Kpi label="max"       value={formatLatency(channel.aggregate.latency.maxMax)}      tone="text-foreground" />
          <Kpi label="in-flight" value={formatNumber(channel.aggregate.totalInFlight)}          tone={valueToneClass(channel.aggregate.totalInFlight)} />
          <Kpi label="errors"    value={formatNumber(channel.aggregate.totalErrCnt)}            tone={valueToneClass(channel.aggregate.totalErrCnt, /*warn*/ true)} />
        </div>
      </button>

      {/* ============================== Body ============================== */}
      {expanded && (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 border-t border-border">
            <section className="p-4 border-b lg:border-b-0 lg:border-r border-border">
              <ColumnHeader
                icon={<Server className="w-3.5 h-3.5" />}
                title="Server"
                meta={
                  channel.listenPort != null
                    ? `listen :${channel.listenPort}`
                    : channel.servers.length === 0 ? '— no server side —' : undefined
                }
              />
              {channel.servers.length === 0 ? (
                <EmptyNote>No server sockets on this channel.</EmptyNote>
              ) : (
                <div className="mt-2 space-y-3">
                  {channel.servers.map((s, i) => (
                    <SocketRow key={s.bindingId ?? `${s.socketId}-${i}`} socket={s} isLast={i === channel.servers.length - 1} />
                  ))}
                </div>
              )}
            </section>
            <section className="p-4">
              <ColumnHeader
                icon={<Radio className="w-3.5 h-3.5" />}
                title="Client"
                meta={
                  channel.clientStrategy
                    ? `strategy: ${channel.clientStrategy}`
                    : channel.clients.length === 0 ? '— no client side —' : undefined
                }
              />
              {channel.clients.length === 0 ? (
                <EmptyNote>No client sockets on this channel.</EmptyNote>
              ) : (
                <div className="mt-2 space-y-3">
                  {channel.clients.map((s, i) => (
                    <SocketRow key={s.bindingId ?? `${s.socketId}-${i}`} socket={s} isLast={i === channel.clients.length - 1} />
                  ))}
                </div>
              )}
            </section>
          </div>

          {/* ============================ Footer ============================ */}
          <footer className="flex items-center gap-2 px-4 py-2.5 border-t border-border bg-muted/30">
            <FooterButton disabled>Start</FooterButton>
            <FooterButton disabled>Stop</FooterButton>
            <FooterButton disabled>Restart</FooterButton>
            <div className="flex-1" />
            <FooterButton variant="primary" onClick={openDetails}>Details →</FooterButton>
          </footer>
        </>
      )}
    </div>
  );
}

// ===========================================================================

function Kpi({ label, value, tone }: { label: string; value: string; tone: string }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <span className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</span>
      <span className={`font-semibold ${tone}`}>{value}</span>
    </div>
  );
}

function ColumnHeader({ icon, title, meta }: { icon: React.ReactNode; title: string; meta?: string }) {
  return (
    <header className="flex items-center gap-2 pb-2 border-b border-border">
      <span className="text-muted-foreground">{icon}</span>
      <span className="text-[11px] font-semibold uppercase tracking-wide text-foreground">{title}</span>
      {meta && <span className="font-mono text-[11px] text-muted-foreground">{meta}</span>}
    </header>
  );
}

function EmptyNote({ children }: { children: React.ReactNode }) {
  return <div className="mt-3 text-[12px] italic text-muted-foreground">{children}</div>;
}

function SocketRow({ socket, isLast }: { socket: SocketSummary; isLast: boolean }) {
  const rt = socket.runtime;
  const connIsNewer = rt.lastConnect >= rt.lastDisconnect;
  const lastLabel = connIsNewer ? 'last_conn' : 'last_dc';
  const lastValue = connIsNewer ? rt.lastConnect : rt.lastDisconnect;

  return (
    <div className={'space-y-1.5 ' + (isLast ? '' : 'pb-3 border-b border-dashed border-border')}>
      <div className="flex items-center gap-2 font-mono text-[11px]">
        <span className={`w-1.5 h-1.5 rounded-full ${socketStatusDotClass(rt.state)}`} />
        <span className="truncate text-foreground">{socket.socketId}</span>
        <span className={`text-[10px] font-semibold ${socketStatusTextClass(rt.state)}`}>{rt.state}</span>
        <div className="flex-1" />
        <span className="text-muted-foreground">
          conn: <span className={valueToneClass(rt.activeChannels)}>{rt.activeChannels}</span>
        </span>
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 font-mono text-[11px] pl-3.5">
        <KV k="local"     v={rt.localHost || '—'} />
        <KV k="remote"    v={rt.remoteHost || '—'} />
        <KV k="uptime"    v={rt.startTime ? formatDurationCompact(Date.now() - rt.startTime) : '—'} />
        <KV k={lastLabel} v={formatAgo(lastValue)} />
      </dl>
    </div>
  );
}

function KV({ k, v }: { k: string; v: string }) {
  return (
    <>
      <dt className="text-muted-foreground">{k}</dt>
      <dd className="text-foreground truncate">{v}</dd>
    </>
  );
}

function FooterButton({
  children,
  disabled,
  variant = 'default',
  onClick,
}: {
  children: React.ReactNode;
  disabled?: boolean;
  variant?: 'default' | 'primary';
  onClick?: () => void;
}) {
  const base = 'px-2.5 py-1 text-[12px] rounded border transition-colors disabled:cursor-not-allowed disabled:opacity-50';
  const tone = variant === 'primary'
    ? 'border-accent text-accent hover:bg-accent/10'
    : 'border-border text-foreground hover:bg-muted/40';
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={`${base} ${tone}`}
      title={disabled ? 'Available in a later chat' : undefined}
    >
      {children}
    </button>
  );
}
