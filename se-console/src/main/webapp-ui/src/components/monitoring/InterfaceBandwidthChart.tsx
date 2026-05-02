import { useEffect, useMemo, useRef, useState } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Card } from '../dashboard/Card';
import type { NetworkInterfaceInfo, NetworkSnapshotRow } from '../../api/network.types';
import { formatBytes } from '../../lib/format';
import { interfaceSeriesFromRows } from '../../lib/snapshotAdapters';

/**
 * Bandwidth chart for a selected interface. RX + TX as rate (bytes/sec)
 * computed from delta between consecutive cumulative snapshots.
 *
 * First sample establishes baseline — no chart point emitted until second
 * poll arrives (same pattern as GcRateChart).
 *
 * Historical mode: `historyRows` is a time-ordered list of
 * NetworkSnapshotRow objects; we parse the embedded `interfacesJson` for
 * the selected iface and compute deltas between consecutive rows.
 */
interface Sample {
  t: number;
  timestamp: number;
  bytesRecv: number;
  bytesSent: number;
}

interface DisplayPoint {
  t: number;
  rx: number;
  tx: number;
}

export function InterfaceBandwidthChart({
  iface,
  timestamp,
  historyRows,
  maxPoints = 60,
}: {
  iface: NetworkInterfaceInfo;
  timestamp: string;
  historyRows?: NetworkSnapshotRow[];
  maxPoints?: number;
}) {
  const isHistory = !!historyRows;
  const startRef = useRef<number>(Date.now());
  const samplesRef = useRef<Sample[]>([]);
  const lastTsRef = useRef<string | null>(null);
  const [displayPoints, setDisplayPoints] = useState<DisplayPoint[]>([]);

  // Reset when the selected interface changes so we don't mix series.
  const ifaceName = iface.name;
  const lastIfaceRef = useRef<string>(ifaceName);
  useEffect(() => {
    if (lastIfaceRef.current !== ifaceName) {
      lastIfaceRef.current = ifaceName;
      samplesRef.current = [];
      lastTsRef.current = null;
      setDisplayPoints([]);
    }
  }, [ifaceName]);

  useEffect(() => {
    if (isHistory) return;
    if (timestamp === lastTsRef.current) return;
    lastTsRef.current = timestamp;

    const now = Date.now();
    const sample: Sample = {
      t: now - startRef.current,
      timestamp: now,
      bytesRecv: iface.bytesRecv,
      bytesSent: iface.bytesSent,
    };

    const buf = samplesRef.current;
    buf.push(sample);
    if (buf.length > maxPoints + 1) buf.shift();

    if (buf.length < 2) return;

    const prev = buf[buf.length - 2];
    const curr = buf[buf.length - 1];
    const dtSec = (curr.timestamp - prev.timestamp) / 1000;
    if (dtSec <= 0) return;

    // Defensive: counters can reset if interface goes down briefly.
    // Drop point if delta is negative (counter rollback).
    const dRx = curr.bytesRecv - prev.bytesRecv;
    const dTx = curr.bytesSent - prev.bytesSent;
    if (dRx < 0 || dTx < 0) return;

    const point: DisplayPoint = {
      t: curr.t,
      rx: dRx / dtSec,
      tx: dTx / dtSec,
    };
    setDisplayPoints((prevPoints) => {
      const next = [...prevPoints, point];
      if (next.length > maxPoints) next.shift();
      return next;
    });
  }, [timestamp, iface.bytesRecv, iface.bytesSent, maxPoints, isHistory]);

  // Historical mode: derive rate points from cumulative byte counters
  // embedded in each snapshot row's interfacesJson.
  const historicalPoints = useMemo<DisplayPoint[]>(() => {
    if (!isHistory) return [];
    const cumulative = interfaceSeriesFromRows(historyRows, iface.name);
    const out: DisplayPoint[] = [];
    for (let i = 1; i < cumulative.length; i++) {
      const prev = cumulative[i - 1];
      const curr = cumulative[i];
      const dtSec = (curr.t - prev.t) / 1000;
      if (dtSec <= 0) continue;
      const dRx = curr.rx - prev.rx;
      const dTx = curr.tx - prev.tx;
      if (dRx < 0 || dTx < 0) continue;    // interface reset mid-window
      out.push({ t: curr.t, rx: dRx / dtSec, tx: dTx / dtSec });
    }
    return out;
  }, [isHistory, historyRows, iface.name]);

  const points = isHistory ? historicalPoints : displayPoints;
  const current = points[points.length - 1];

  // Peak over the window (for stat bar + upper visual reference).
  const peaks = points.reduce(
    (acc, p) => ({
      rx: Math.max(acc.rx, p.rx),
      tx: Math.max(acc.tx, p.tx),
    }),
    { rx: 0, tx: 0 }
  );

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3 flex-wrap">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Interface Bandwidth
          </div>
          <div className="flex items-baseline leading-none gap-3">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
              title={`${iface.displayName} (${iface.name})`}
            >
              {iface.name}
            </span>
            {iface.speedBitsPerSecond > 0 && (
              <span className="text-[12px] text-muted-foreground">
                {formatLinkSpeed(iface.speedBitsPerSecond)}
              </span>
            )}
            {!iface.up && (
              <span className="text-[11px] px-1.5 py-0.5 rounded bg-destructive/10 text-destructive font-medium">
                DOWN
              </span>
            )}
          </div>
        </div>
        <div className="flex gap-3 text-[11px] flex-wrap justify-end text-muted-foreground">
          {current && (
            <>
              <Stat label="RX" value={`${formatBytes(current.rx)}/s`} color="hsl(var(--accent))" />
              <Stat label="TX" value={`${formatBytes(current.tx)}/s`} color="hsl(var(--primary))" />
            </>
          )}
          <Stat label="Peak RX" value={`${formatBytes(peaks.rx)}/s`} />
          <Stat label="Peak TX" value={`${formatBytes(peaks.tx)}/s`} />
        </div>
      </div>

      <div style={{ height: 180 }}>
        {points.length < 2 ? (
          <div className="h-full flex items-center justify-center text-[12px] text-muted-foreground">
            {isHistory ? 'Not enough samples in range' : 'Collecting samples…'}
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={points} margin={{ top: 5, right: 10, left: -10, bottom: 0 }}>
              <defs>
                <linearGradient id="ifRx" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="hsl(var(--accent))" stopOpacity={0.25} />
                  <stop offset="100%" stopColor="hsl(var(--accent))" stopOpacity={0} />
                </linearGradient>
                <linearGradient id="ifTx" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity={0.25} />
                  <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="hsl(var(--border))" vertical={false} strokeDasharray="2 4" />
              <XAxis dataKey="t" hide />
              <YAxis
                tick={{ fontSize: 10, fill: 'hsl(var(--muted-foreground))' }}
                tickFormatter={(v: number) => formatBytes(v).replace(' ', '')}
                width={60}
              />
              <Tooltip
                contentStyle={{
                  background: 'hsl(var(--card))',
                  border: '1px solid hsl(var(--border))',
                  borderRadius: 4,
                  fontSize: 11,
                }}
                labelFormatter={(t) =>
                  isHistory ? new Date(Number(t)).toLocaleString() : ''
                }
                formatter={(value, name) => [
                  `${formatBytes(Number(value))}/s`,
                  name === 'rx' ? 'RX (inbound)' : 'TX (outbound)',
                ]}
              />
              <Area
                type="monotone"
                dataKey="rx"
                stroke="hsl(var(--accent))"
                strokeWidth={1.5}
                fill="url(#ifRx)"
                isAnimationActive={false}
              />
              <Area
                type="monotone"
                dataKey="tx"
                stroke="hsl(var(--primary))"
                strokeWidth={1.5}
                fill="url(#ifTx)"
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="flex gap-4 mt-3 text-[11px]">
        <LegendItem color="hsl(var(--accent))" label="RX (received)" />
        <LegendItem color="hsl(var(--primary))" label="TX (transmitted)" />
      </div>
    </Card>
  );
}

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div className="flex items-center gap-1">
      {color && <span className="w-2 h-2 rounded-sm" style={{ background: color }} />}
      <span>{label}</span>
      <b className="font-mono text-foreground font-medium tabular-nums">{value}</b>
    </div>
  );
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className="w-3 h-0.5" style={{ background: color }} />
      <span className="text-muted-foreground">{label}</span>
    </div>
  );
}

/** Format link speed: 1,000,000,000 bps → "1 Gbps". */
function formatLinkSpeed(bps: number): string {
  if (bps >= 1e9) return `${(bps / 1e9).toFixed(bps >= 1e10 ? 0 : 0)} Gbps`;
  if (bps >= 1e6) return `${Math.round(bps / 1e6)} Mbps`;
  if (bps >= 1e3) return `${Math.round(bps / 1e3)} Kbps`;
  return `${bps} bps`;
}
