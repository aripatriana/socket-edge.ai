import { useEffect, useRef, useState } from 'react';

/**
 * Tracks a rolling history of numeric values, appending one point each time
 * the component re-renders with a fresh poll result. Used by chart
 * components to render a sparkline without the backend needing to keep history.
 *
 * Design note: the hook takes both the metric value AND a trigger (usually
 * the poll timestamp) as inputs. The trigger is what decides "push a new
 * point"; the value is what gets pushed. This prevents the bug where a
 * metric that's consistently 0.0 (e.g. CPU on idle Windows) never advances
 * the buffer because React treats the value as unchanged.
 */
export function useMetricHistory(
  value: number | null | undefined,
  maxPoints = 60,
  trigger?: number | string | null
) {
  const [history, setHistory] = useState<{ t: number; v: number }[]>([]);
  const startRef = useRef<number>(Date.now());
  const lastTriggerRef = useRef<number | string | null | undefined>(undefined);

  useEffect(() => {
    if (trigger !== undefined) {
      if (trigger === lastTriggerRef.current) return;
      lastTriggerRef.current = trigger;
    }
    if (value == null || Number.isNaN(value)) return;
    const point = { t: Date.now() - startRef.current, v: value };
    setHistory((prev) => {
      const next = [...prev, point];
      if (next.length > maxPoints) next.shift();
      return next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [trigger, value]);

  return history;
}
