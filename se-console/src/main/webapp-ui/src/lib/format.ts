/**
 * Formatting helpers for metrics display.
 * Consolidated: Chat 3a base helpers + Chat 3b+ additions (formatCpuTime, formatRate).
 */

/** Format a byte count into human-readable units (KB, MB, GB, TB). */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || bytes < 0) return 'N/A';
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const exp = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, exp);
  const decimals = exp === 0 ? 0 : value >= 100 ? 0 : value >= 10 ? 1 : 1;
  return `${value.toFixed(decimals)} ${units[exp]}`;
}

/** Format a percentage (0-100), with 1 decimal place. "N/A" if null. */
export function formatPercent(pct: number | null | undefined): string {
  if (pct == null || !Number.isFinite(pct)) return 'N/A';
  return `${pct.toFixed(1)}%`;
}

/** Format an integer with thousands separators. */
export function formatNumber(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n)) return 'N/A';
  return n.toLocaleString();
}

/**
 * Format a seconds count as uptime: "2h 48m", "3d 5h", "45s".
 * Seconds-grained precision; for durations longer than a minute, the
 * seconds are dropped to keep the display compact.
 */
export function formatUptime(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0) return 'N/A';
  const s = Math.floor(seconds);
  const days = Math.floor(s / 86400);
  const hours = Math.floor((s % 86400) / 3600);
  const mins = Math.floor((s % 3600) / 60);
  const secs = s % 60;

  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${mins}m`;
  if (mins > 0) return `${mins}m ${secs}s`;
  return `${secs}s`;
}

// ==========================================================================
// Chat 3b+ additions — CPU time and rate formatting for thread list + GC
// ==========================================================================

/**
 * Format nanoseconds CPU time to human units.
 * < 1ms → "0.50 ms", < 1s → "234 ms", ≥ 1s → "1.2 s".
 * Returns "—" when null (unavailable / thread terminated).
 */
export function formatCpuTime(ns: number | null | undefined): string {
  if (ns == null) return '—';
  const ms = ns / 1_000_000;
  if (ms < 1) return `${ms.toFixed(2)} ms`;
  if (ms < 1_000) return `${Math.round(ms)} ms`;
  return `${(ms / 1_000).toFixed(1)} s`;
}

/**
 * Format a rate value (events per minute) with 1-decimal precision when
 * < 10, 0-decimal when larger. "N/A" when null/non-finite.
 */
export function formatRate(rate: number | null | undefined): string {
  if (rate == null || !Number.isFinite(rate)) return 'N/A';
  if (rate < 10) return rate.toFixed(1);
  return Math.round(rate).toString();
}
