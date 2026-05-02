// Engine-specific formatters. Kept separate from lib/format.ts so baseline
// formatters remain untouched. Conventions match the CLI jstatus.sh style.

import type { ChannelState, SocketStatus } from '../api/channels.types';

// ==========================================================================
// Latency — auto-scale ns → µs → ms → s
// ==========================================================================

/** Format nanoseconds with the appropriate unit. 0 → "—" placeholder. */
export function formatLatency(ns: number): string {
  if (!ns || ns <= 0) return '—';
  if (ns < 1_000)        return `${ns}ns`;
  if (ns < 1_000_000)    return `${(ns / 1_000).toFixed(1)}µs`;
  if (ns < 1_000_000_000) return `${(ns / 1_000_000).toFixed(2)}ms`;
  return `${(ns / 1_000_000_000).toFixed(2)}s`;
}

// ==========================================================================
// Compact duration — "1h35m54s", "25m23s", "31s" (skip leading zero units)
// ==========================================================================

/**
 * Format a millisecond duration in CLI-style compact form.
 * 3_723_000 → "1h2m3s", 83_000 → "1m23s", 31_000 → "31s".
 */
export function formatDurationCompact(ms: number): string {
  if (!ms || ms <= 0) return '—';
  const total = Math.floor(ms / 1000);
  const d  = Math.floor(total / 86_400);
  const h  = Math.floor((total % 86_400) / 3_600);
  const m  = Math.floor((total % 3_600) / 60);
  const s  = total % 60;

  let out = '';
  if (d > 0) out += `${d}d`;
  if (d > 0 || h > 0) out += `${h}h`;
  if (d > 0 || h > 0 || m > 0) out += `${m}m`;
  out += `${s}s`;
  return out;
}

/** Relative "time since" for epoch millis. */
export function formatAgo(epochMs: number): string {
  if (!epochMs || epochMs <= 0) return '—';
  const delta = Date.now() - epochMs;
  if (delta < 0) return '—';
  return formatDurationCompact(delta);
}

// ==========================================================================
// State → colors
//
// Baseline design tokens don't define --status-up/down. Use Tailwind's
// emerald / amber / red — same palette used elsewhere in the app
// (see Topbar's bg-emerald-500 pulse dot).
// ==========================================================================

/** Tailwind text color class for a socket status. */
export function socketStatusTextClass(s: SocketStatus | string): string {
  switch ((s || '').toUpperCase()) {
    case 'ACTIVE':
    case 'LISTEN':
      return 'text-emerald-600';
    case 'STANDBY':
      return 'text-sky-600';
    case 'WAIT':
      return 'text-amber-600';
    case 'DOWN':
      return 'text-amber-700';
    case 'ERROR':
      return 'text-red-600';
    default:
      return 'text-muted-foreground';
  }
}

/** Tailwind background color class for the status dot glyph. */
export function socketStatusDotClass(s: SocketStatus | string): string {
  switch ((s || '').toUpperCase()) {
    case 'ACTIVE':
    case 'LISTEN':
      return 'bg-emerald-500';
    case 'STANDBY':
      return 'bg-sky-500';
    case 'WAIT':
    case 'DOWN':
      return 'bg-amber-500';
    case 'ERROR':
      return 'bg-red-500';
    default:
      return 'bg-slate-400';
  }
}

/** Channel-level aggregate state → badge label + Tailwind classes. */
export function channelStateBadge(state: ChannelState | string): { label: string; className: string } {
  switch ((state || '').toUpperCase()) {
    case 'ACTIVE':
      return {
        label: 'ACTIVE',
        className: 'bg-emerald-50 text-emerald-700 border-emerald-200',
      };
    case 'DEGRADED':
      return {
        label: 'DEGRADED',
        className: 'bg-amber-50 text-amber-700 border-amber-200',
      };
    case 'DOWN':
      return {
        label: 'DOWN',
        className: 'bg-slate-100 text-slate-600 border-slate-200',
      };
    case 'ERROR':
      return {
        label: 'ERROR',
        className: 'bg-red-50 text-red-700 border-red-200',
      };
    default:
      return {
        label: 'UNKNOWN',
        className: 'bg-muted text-muted-foreground border-border',
      };
  }
}

/** Grey out zero values per spec: "0 TPS / 0 errors greyed; >0 primary/warning". */
export function valueToneClass(n: number, warnWhenNonzero = false): string {
  if (!n || n <= 0) return 'text-muted-foreground';
  return warnWhenNonzero ? 'text-red-600' : 'text-foreground';
}
