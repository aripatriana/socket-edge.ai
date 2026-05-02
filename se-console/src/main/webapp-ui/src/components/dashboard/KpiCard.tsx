import type { ReactNode } from 'react';
import { Sparkline } from './Sparkline';
import { Card } from './Card';

/**
 * KPI card — large monospace number with optional unit, sub-text, and
 * sparkline. Used in the 4-column grid below the health strip.
 */
export function KpiCard({
  label,
  value,
  unit,
  sub,
  history,
  sparkColor,
  yDomain,
}: {
  label: string;
  value: string;
  unit?: string;
  sub?: ReactNode;
  history?: { t: number; v: number }[];
  sparkColor?: string;
  yDomain?: [number, number];
}) {
  return (
    <Card className="relative overflow-hidden px-4 py-3.5 min-h-[96px]">
      <div className="text-[11px] uppercase tracking-wider font-semibold text-muted-foreground mb-2">
        {label}
      </div>
      <div className="flex items-baseline">
        <span
          className="font-mono font-medium text-foreground tabular-nums"
          style={{ fontSize: 28, lineHeight: 1.1, letterSpacing: '-0.5px' }}
        >
          {value}
        </span>
        {unit && (
          <span className="ml-1 text-[13px] text-muted-foreground font-normal">
            {unit}
          </span>
        )}
      </div>
      {sub && (
        <div className="flex items-center gap-1 mt-1.5 text-[11px] text-muted-foreground">
          {sub}
        </div>
      )}
      {history && history.length >= 2 && (
        <div className="absolute w-20 h-8 opacity-75" style={{ bottom: 10, right: 14 }}>
          <Sparkline data={history} color={sparkColor} yDomain={yDomain} />
        </div>
      )}
    </Card>
  );
}
