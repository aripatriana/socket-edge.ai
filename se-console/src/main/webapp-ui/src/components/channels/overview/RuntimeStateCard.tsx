import type { ChannelSummary, SocketSummary } from '../../../api/channels.types';
import {
  channelStateBadge,
  formatDurationCompact,
  formatAgo,
  socketStatusDotClass,
  socketStatusTextClass,
} from '../../../lib/formatEngine';
import { formatNumber } from '../../../lib/format';

/**
 * Runtime state card — server-side focused.
 *
 * <p>Per requirement "runtime state dari server punya aja": the lifecycle
 * data (state, uptime, last activity) is derived from the channel's
 * {@code servers} list. For channels with no server side (client-only),
 * we show a single "client-only channel" note so the UI isn't empty.
 *
 * <p>Only engine-backed fields are shown — no placeholder values for
 * metrics the engine doesn't actually expose (match rate, memory, etc).
 */
export function RuntimeStateCard({ channel }: { channel: ChannelSummary }) {
  const badge = channelStateBadge(channel.aggregateState);
  const servers = channel.servers;
  const nowMs = Date.now();

  // No server side → short form.
  if (servers.length === 0) {
    return (
      <section className="rounded-md border border-border bg-card p-4">
        <Header />
        <div className="text-[12px] italic text-muted-foreground mt-2">
          Client-only channel — no server-side runtime state to show.
        </div>
      </section>
    );
  }

  const primary = pickPrimaryServer(servers);
  const rt = primary.runtime;
  const q = primary.queue;
  const agg = channel.aggregate;
  const uptimeMs = rt.startTime > 0 ? nowMs - rt.startTime : 0;
  const lastActivityMs = Math.max(rt.lastConnect, q.lastMsg);
  const lastErrMs = q.lastErr;

  return (
    <section className="rounded-md border border-border bg-card p-4">
      <Header />

      <dl className="grid grid-cols-[130px_1fr] gap-x-4 gap-y-2 text-[12px] font-mono">
        <Row label="state">
          <span
            className={`inline-flex items-center px-2 py-0.5 rounded border text-[10px] font-semibold tracking-wide ${badge.className}`}
          >
            {badge.label}
          </span>
        </Row>

        <Row label="server">
          <span className="inline-flex items-center gap-1.5">
            <span className={`w-1.5 h-1.5 rounded-full ${socketStatusDotClass(rt.state)}`} />
            <span className={`text-[10px] font-semibold ${socketStatusTextClass(rt.state)}`}>
              {rt.state}
            </span>
            <span className="text-muted-foreground text-[10px]">{rt.localHost || '—'}</span>
          </span>
        </Row>

        <Row label="sockets up">
          <span className={channel.socketsUp === channel.socketsTotal ? 'text-emerald-600' : 'text-amber-600'}>
            {channel.socketsUp}
          </span>
          <span className="text-muted-foreground"> / {channel.socketsTotal}</span>
        </Row>

        <Row label="active conns">
          <span className="text-foreground">{formatNumber(rt.activeChannels)}</span>
        </Row>

        <Row label="uptime">
          <span className="text-foreground">
            {uptimeMs > 0 ? formatDurationCompact(uptimeMs) : '—'}
          </span>
        </Row>

        <Row label="last activity">
          <span className="text-foreground">
            {lastActivityMs > 0 ? formatAgo(lastActivityMs) : '—'}
          </span>
        </Row>

        <Row label="messages in">
          <span className="text-foreground">{formatNumber(agg.totalMsgIn)}</span>
        </Row>

        <Row label="messages out">
          <span className="text-foreground">
            {formatNumber(agg.totalMsgOut)}
            {agg.totalMsgIn !== agg.totalMsgOut && (
              <span className="text-muted-foreground ml-2">
                Δ {formatNumber(Math.abs(agg.totalMsgIn - agg.totalMsgOut))}
              </span>
            )}
          </span>
        </Row>

        <Row label="errors">
          <span className={agg.totalErrCnt > 0 ? 'text-red-600' : 'text-foreground'}>
            {formatNumber(agg.totalErrCnt)}
          </span>
        </Row>

        {lastErrMs > 0 && (
          <Row label="last error">
            <span className="text-red-600">{formatAgo(lastErrMs)}</span>
          </Row>
        )}
      </dl>
    </section>
  );
}

// ---------------------------------------------------------------------------

function Header() {
  return (
    <header className="text-[10px] uppercase tracking-wider font-semibold text-muted-foreground mb-3">
      RUNTIME STATE
    </header>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <>
      <dt className="text-muted-foreground uppercase tracking-wider text-[10px] self-center">
        {label}
      </dt>
      <dd>{children}</dd>
    </>
  );
}

/**
 * Pick the "primary" server socket for display. There's usually only one
 * server socket per channel (the listen), but be safe: prefer ACTIVE/LISTEN,
 * then fall back to the earliest-started one.
 */
function pickPrimaryServer(servers: SocketSummary[]): SocketSummary {
  const healthy = servers.find(
    (s) => s.runtime.state === 'ACTIVE' || s.runtime.state === 'LISTEN'
  );
  if (healthy) return healthy;

  // Earliest start.
  return [...servers].sort((a, b) => {
    const aT = a.runtime.startTime || Number.POSITIVE_INFINITY;
    const bT = b.runtime.startTime || Number.POSITIVE_INFINITY;
    return aT - bT;
  })[0];
}
