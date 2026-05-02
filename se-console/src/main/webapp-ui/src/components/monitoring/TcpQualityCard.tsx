import { useEffect, useRef, useState } from 'react';
import { Card } from '../dashboard/Card';
import type { TcpQualityCounters } from '../../api/network.types';
import { formatNumber } from '../../lib/format';

/**
 * TCP stack quality metrics — retransmits, resets, listen drops.
 * Computed as rate (per second) from delta between consecutive cumulative
 * snapshots; same delta pattern as GcRateChart / InterfaceBandwidthChart.
 *
 * Linux-only (from /proc/net/snmp and /proc/net/netstat). On Windows,
 * renders "Linux only" placeholder.
 */
interface Sample {
  timestamp: number;
  retransSegs: number | null;
  outSegs: number | null;
  outResets: number | null;
  inErrs: number | null;
  listenDrops: number | null;
  listenOverflows: number | null;
}

interface Rates {
  retransPerSec: number;
  retransPct: number;
  resetsPerSec: number;
  inErrsPerSec: number;
  listenDropsPerSec: number;
  listenOverflowsPerSec: number;
}

export function TcpQualityCard({
  quality,
  timestamp,
}: {
  quality: TcpQualityCounters;
  timestamp: string;
}) {
  const samplesRef = useRef<Sample[]>([]);
  const lastTsRef = useRef<string | null>(null);
  const [rates, setRates] = useState<Rates | null>(null);

  // Check Linux-availability — if all counters are null, we can't compute rates.
  const isLinux =
    quality.retransSegs != null ||
    quality.outSegs != null ||
    quality.outResets != null;

  useEffect(() => {
    if (!isLinux) return;
    if (timestamp === lastTsRef.current) return;
    lastTsRef.current = timestamp;

    const now = Date.now();
    const sample: Sample = {
      timestamp: now,
      retransSegs: quality.retransSegs,
      outSegs: quality.outSegs,
      outResets: quality.outResets,
      inErrs: quality.inErrs,
      listenDrops: quality.listenDrops,
      listenOverflows: quality.listenOverflows,
    };

    const buf = samplesRef.current;
    buf.push(sample);
    if (buf.length > 60) buf.shift();

    if (buf.length < 2) return;

    const prev = buf[buf.length - 2];
    const curr = buf[buf.length - 1];
    const dtSec = (curr.timestamp - prev.timestamp) / 1000;
    if (dtSec <= 0) return;

    const dRetrans = diff(curr.retransSegs, prev.retransSegs);
    const dOutSegs = diff(curr.outSegs, prev.outSegs);
    const dResets = diff(curr.outResets, prev.outResets);
    const dInErrs = diff(curr.inErrs, prev.inErrs);
    const dListenDrops = diff(curr.listenDrops, prev.listenDrops);
    const dListenOverflows = diff(curr.listenOverflows, prev.listenOverflows);

    const retransPerSec = dRetrans / dtSec;
    const outSegsPerSec = dOutSegs / dtSec;
    const retransPct = outSegsPerSec > 0 ? (retransPerSec / outSegsPerSec) * 100 : 0;

    setRates({
      retransPerSec,
      retransPct,
      resetsPerSec: dResets / dtSec,
      inErrsPerSec: dInErrs / dtSec,
      listenDropsPerSec: dListenDrops / dtSec,
      listenOverflowsPerSec: dListenOverflows / dtSec,
    });
  }, [timestamp, quality, isLinux]);

  if (!isLinux) {
    return (
      <Card className="px-4 py-3.5">
        <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
          TCP Quality
        </div>
        <div className="flex items-center justify-center h-[180px] text-[13px] text-muted-foreground">
          Linux only
        </div>
      </Card>
    );
  }

  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            TCP Quality
          </div>
          <div className="flex items-baseline leading-none">
            <span
              className="font-mono font-medium text-foreground tabular-nums"
              style={{ fontSize: 22 }}
            >
              {rates != null ? `${rates.retransPct.toFixed(3)}%` : '—'}
            </span>
            <span className="ml-1 text-[12px] text-muted-foreground">retransmit rate</span>
          </div>
        </div>
        {quality.currEstab != null && (
          <div className="text-[11px] text-muted-foreground text-right">
            <div>Current established</div>
            <b className="font-mono text-foreground font-medium tabular-nums">
              {formatNumber(quality.currEstab)}
            </b>
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-x-6 gap-y-2.5 text-[11px]">
        <Metric
          label="Retransmits"
          rate={rates?.retransPerSec}
          total={quality.retransSegs}
          unit="/s"
          warningThreshold={10}
        />
        <Metric
          label="Retransmit %"
          rate={rates?.retransPct}
          unit="%"
          decimals={3}
          warningThreshold={1}
          dangerThreshold={5}
        />
        <Metric
          label="Resets out"
          rate={rates?.resetsPerSec}
          total={quality.outResets}
          unit="/s"
          warningThreshold={5}
        />
        <Metric
          label="Errors in"
          rate={rates?.inErrsPerSec}
          total={quality.inErrs}
          unit="/s"
          warningThreshold={5}
        />
        <Metric
          label="Listen drops"
          rate={rates?.listenDropsPerSec}
          total={quality.listenDrops}
          unit="/s"
          dangerThreshold={1}
        />
        <Metric
          label="Listen overflows"
          rate={rates?.listenOverflowsPerSec}
          total={quality.listenOverflows}
          unit="/s"
          dangerThreshold={1}
        />
      </div>

      {quality.syncookiesSent != null && quality.syncookiesSent > 0 && (
        <div className="text-[11px] text-muted-foreground mt-3 pt-3 border-t border-border">
          <span>SYN cookies sent (cumulative): </span>
          <b className="font-mono text-foreground font-medium tabular-nums">
            {formatNumber(quality.syncookiesSent)}
          </b>
          <span className="ml-2 text-amber-600">
            Non-zero — kernel fell back to SYN cookies at some point
          </span>
        </div>
      )}
    </Card>
  );
}

function Metric({
  label,
  rate,
  total,
  unit,
  decimals = 2,
  warningThreshold,
  dangerThreshold,
}: {
  label: string;
  rate: number | null | undefined;
  total?: number | null;
  unit: string;
  decimals?: number;
  warningThreshold?: number;
  dangerThreshold?: number;
}) {
  const value = rate ?? 0;
  let className = 'text-foreground';
  if (rate != null && dangerThreshold != null && value >= dangerThreshold) {
    className = 'text-destructive font-semibold';
  } else if (rate != null && warningThreshold != null && value >= warningThreshold) {
    className = 'text-amber-600 font-semibold';
  }

  return (
    <div className="flex items-baseline justify-between gap-2">
      <span className="text-muted-foreground">{label}</span>
      <div className="flex items-baseline gap-2">
        <span className={`font-mono tabular-nums ${className}`}>
          {rate != null ? rate.toFixed(decimals) : '—'}{unit}
        </span>
        {total != null && (
          <span className="font-mono text-[10px] text-muted-foreground tabular-nums">
            total {formatNumber(total)}
          </span>
        )}
      </div>
    </div>
  );
}

function diff(curr: number | null | undefined, prev: number | null | undefined): number {
  if (curr == null || prev == null) return 0;
  const d = curr - prev;
  return d >= 0 ? d : 0; // guard counter rollback
}
