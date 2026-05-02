import type { ReactNode } from 'react';
import { Card } from './Card';

/**
 * Full-size chart wrapper with header (title + current value) and footer-stats.
 */
export function ChartCard({
  title,
  current,
  unit,
  stats,
  children,
  height = 160,
}: {
  title: string;
  current?: string;
  unit?: string;
  stats?: ReactNode;
  children: ReactNode;
  height?: number | 'auto';
}) {
  return (
    <Card className="px-4 py-3.5">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div className="flex flex-col gap-1">
          <div className="text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            {title}
          </div>
          {current != null && (
            <div className="flex items-baseline leading-none">
              <span
                className="font-mono font-medium text-foreground tabular-nums"
                style={{ fontSize: 22 }}
              >
                {current}
              </span>
              {unit && (
                <span className="ml-1 text-[12px] text-muted-foreground">
                  {unit}
                </span>
              )}
            </div>
          )}
        </div>
        {stats && (
          <div className="flex gap-3 text-[11px] flex-wrap justify-end text-muted-foreground">
            {stats}
          </div>
        )}
      </div>
      <div style={{ height: height === 'auto' ? undefined : height }}>{children}</div>
    </Card>
  );
}

export function ChartStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-1">
      <span>{label}</span>
      <b className="font-mono font-medium text-foreground tabular-nums">{value}</b>
    </div>
  );
}
