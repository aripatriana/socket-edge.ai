import { useEngineHealth } from '../../hooks/useEngineHealth';

/**
 * Topbar indicator: engine reachability + base URL + role.
 *
 * Renders as: `● socket-edge · 127.0.0.1:9001 · MASTER`
 *
 * Dot pulses emerald when the engine is reachable, stays solid red when not.
 * Protocol prefix is stripped from base URL for compactness. Role/mode only
 * shown when the engine returns them.
 */
export function EngineHealthBadge() {
  const { data, isLoading } = useEngineHealth();

  const reachable = !!data?.reachable;
  const role = data?.role;
  const mode = data?.mode;
  const status = data?.status;
  const baseUrl = (data?.baseUrl ?? '').replace(/^https?:\/\//, '');

  const dotClass = reachable
    ? 'bg-emerald-500 pulse-dot'
    : 'bg-red-500';

  const parts: string[] = ['socket-edge'];
  if (baseUrl) parts.push(baseUrl);
  if (role) parts.push(role);
  if (mode && mode !== 'STANDALONE') parts.push(mode);

  const title = reachable
    ? `Engine reachable · ${status ?? 'OK'}${role ? ` · ${role}` : ''}${mode ? ` · ${mode}` : ''}`
    : data?.lastError
      ? `Engine unreachable: ${data.lastError}`
      : 'Engine unreachable';

  return (
    <div
      className="inline-flex items-center gap-2 px-2.5 py-1 rounded border border-border bg-secondary text-[12px]"
      title={title}
    >
      <span className={`inline-block w-1.5 h-1.5 rounded-full ${dotClass}`} />
      <span className="font-mono text-muted-foreground">
        {isLoading && !data ? 'connecting…' : parts.join(' · ')}
      </span>
    </div>
  );
}
