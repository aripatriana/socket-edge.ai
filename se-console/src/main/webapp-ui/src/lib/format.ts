/**
 * Display helpers. Keep separate from components so they're easy to unit-test
 * later and reusable across Dashboard + Monitoring tabs.
 */

/** 1024-based formatting. 0 → "0 B", 1536 → "1.5 KB". */
export function formatBytes(bytes: number | null | undefined, fractionDigits = 1): string {
  if (bytes == null || Number.isNaN(bytes)) return 'N/A';
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.min(Math.floor(Math.log(Math.abs(bytes)) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, i);
  // Avoid "1.0 KB" — drop decimals for whole numbers.
  const str = Number.isInteger(value) ? value.toString() : value.toFixed(fractionDigits);
  return `${str} ${units[i]}`;
}

/** Uptime in seconds → "3d 14h 22m" or "22m 05s". */
export function formatUptime(totalSeconds: number | null | undefined): string {
  if (totalSeconds == null || totalSeconds < 0) return 'N/A';
  const s = Math.floor(totalSeconds);
  const days = Math.floor(s / 86400);
  const hours = Math.floor((s % 86400) / 3600);
  const minutes = Math.floor((s % 3600) / 60);
  const seconds = s % 60;

  if (days > 0) return `${days}d ${hours}h ${minutes}m`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
  return `${seconds}s`;
}

/** Percentage with one decimal, or "N/A". */
export function formatPercent(value: number | null | undefined, fractionDigits = 1): string {
  if (value == null || Number.isNaN(value)) return 'N/A';
  return `${value.toFixed(fractionDigits)}%`;
}

/** Raw number or "N/A" */
export function formatNumber(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return 'N/A';
  return n.toLocaleString();
}
