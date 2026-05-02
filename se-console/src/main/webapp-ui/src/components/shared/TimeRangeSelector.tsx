import { useMemo, useState } from 'react';

/**
 * Preset time-range selector for monitoring charts. Matches the
 * 1h / 6h / 24h design note in the SystemPage header comment.
 *
 * Also exposes "Live" — no range, chart uses frontend-side rolling buffer
 * (pre-refactor behaviour). History selection triggers a backend DB
 * range query via the matching use*History hook.
 */
export type TimeRangePreset = 'live' | '1h' | '6h' | '24h';

interface Preset {
  value: TimeRangePreset;
  label: string;
  /** Minutes back from "now" — null for Live (no backend fetch). */
  minutesBack: number | null;
}

const PRESETS: Preset[] = [
  { value: 'live', label: 'Live',  minutesBack: null },
  { value: '1h',   label: '1h',    minutesBack: 60 },
  { value: '6h',   label: '6h',    minutesBack: 360 },
  { value: '24h',  label: '24h',   minutesBack: 1440 },
];

export function TimeRangeSelector({
  value,
  onChange,
}: {
  value: TimeRangePreset;
  onChange: (next: TimeRangePreset) => void;
}) {
  return (
    <div
      role="radiogroup"
      aria-label="Time range"
      className="inline-flex gap-0.5 rounded-md border border-border bg-card p-0.5"
    >
      {PRESETS.map((preset) => {
        const active = preset.value === value;
        return (
          <button
            key={preset.value}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(preset.value)}
            className={[
              'px-3 py-1 text-[12px] rounded transition-colors',
              active
                ? 'bg-primary text-primary-foreground font-semibold'
                : 'text-muted-foreground hover:text-foreground',
            ].join(' ')}
          >
            {preset.label}
          </button>
        );
      })}
    </div>
  );
}

/**
 * Pairs with TimeRangeSelector. Returns the ISO-8601 from/to pair for the
 * selected preset, or (null, null) for Live. Memoized on the preset AND a
 * manually-bumpable nonce so callers can "refresh" the window.
 */
export function useTimeRange(initial: TimeRangePreset = 'live') {
  const [preset, setPreset] = useState<TimeRangePreset>(initial);

  // Deliberately re-evaluate when preset changes — each preset change
  // picks a fresh "now". Same preset re-selection is a no-op here; add
  // a refreshKey if a manual refresh button is wired later.
  const { from, to } = useMemo(() => {
    const matched = PRESETS.find((p) => p.value === preset);
    if (!matched || matched.minutesBack === null) {
      return { from: null, to: null };
    }
    const now = new Date();
    const fromDate = new Date(now.getTime() - matched.minutesBack * 60_000);
    return {
      from: fromDate.toISOString(),
      to: now.toISOString(),
    };
  }, [preset]);

  return { preset, setPreset, from, to, isLive: preset === 'live' };
}
