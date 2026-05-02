import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { configApi } from '../api/config';
import type {
  ConfigFileContent,
  ConfigFilesListResponse,
  ValidateResponse,
  ApplyConfigResponse,
  ReloadConfigResponse,
  RestartConfigResponse,
} from '../api/config.types';
import { validateHocon, isFrontendValidatedFile } from '../lib/hoconValidator';

/**
 * List of config files in /conf directory. Slow-poll (60s) — list rarely
 * changes during an editing session.
 */
export function useConfigFiles() {
  return useQuery<ConfigFilesListResponse>({
    queryKey: ['config-files'],
    queryFn: () => configApi.list(),
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
}

/**
 * Single file content. Fetched when user selects a file in the panel.
 * Cached per filename so tab-switching doesn't refetch.
 */
export function useConfigFile(name: string | null) {
  return useQuery<ConfigFileContent>({
    queryKey: ['config-file', name],
    queryFn: () => configApi.read(name!),
    enabled: !!name,
    refetchOnWindowFocus: false,
    staleTime: Infinity,   // only refresh on explicit invalidate (after apply)
  });
}

/**
 * Validate a config file's draft content.
 *
 * <p>Routing rule (see {@link isFrontendValidatedFile}):
 * <ul>
 *   <li>{@code system.conf}, {@code cluster.conf} — HOCON, validated in the
 *       browser via {@link validateHocon}. No network round-trip, result
 *       is instant. Rationale: the engine parses HOCON on restart anyway,
 *       and a cheap browser-side syntax check covers the 95% case
 *       (typos, missing braces) without backend complexity.</li>
 *   <li>{@code channel.conf} — custom DSL, validated by backend (which is
 *       currently a no-op pending engine-side validator — the engine is
 *       the source of truth on reload). Same code path as before.</li>
 * </ul>
 *
 * <p>Both branches return the same {@link ValidateResponse} shape so the
 * calling component doesn't branch. The mutation shape is preserved so
 * {@code .isPending} and {@code .mutateAsync} keep working.
 */
export function useValidateConfig() {
  return useMutation<ValidateResponse, Error, { name: string; content: string }>({
    mutationFn: async ({ name, content }) => {
      if (isFrontendValidatedFile(name)) {
        return validateLocally(content);
      }
      return configApi.validate({ name, content });
    },
  });
}

/**
 * Run the browser-side HOCON validator and wrap the result in the same
 * envelope shape as the backend's {@code /api/config/validate}.
 *
 * <p>Duration is measured in microsecond-resolution via {@code performance.now()}
 * rather than {@code Date.now()} — for a sub-ms operation the cruder clock
 * reports 0ms, which looks broken in the UI.
 */
function validateLocally(content: string): ValidateResponse {
  const started = performance.now();
  const problems = validateHocon(content);
  const errors = problems.filter((p) => p.severity === 'error');
  const warnings = problems.filter((p) => p.severity === 'warning');
  const durationMs = Math.max(1, Math.round(performance.now() - started));
  return {
    valid: errors.length === 0,
    errors,
    warnings,
    durationMs,
  };
}

export function useApplyConfig() {
  const qc = useQueryClient();
  return useMutation<
    ApplyConfigResponse,
    Error,
    { name: string; content: string; expectedVersion: number }
  >({
    mutationFn: ({ name, content, expectedVersion }) =>
      configApi.apply(name, { content, expectedVersion }),
    onSuccess: (_data, variables) => {
      // Refresh file content (version bumped) and list (lastModified updated).
      qc.invalidateQueries({ queryKey: ['config-file', variables.name] });
      qc.invalidateQueries({ queryKey: ['config-files'] });
      // History list needs a refresh to show the new version.
      qc.invalidateQueries({ queryKey: ['config-history', variables.name] });
      // Channels list / detail may react to channel.conf changes.
      if (variables.name === 'channel.conf') {
        qc.invalidateQueries({ queryKey: ['channels'] });
        qc.invalidateQueries({ queryKey: ['channel-detail'] });
        qc.invalidateQueries({ queryKey: ['channel-config'] });
      }
    },
  });
}

/**
 * Trigger an engine config reload. Independent of Apply — operators may
 * reload without applying (useful if the engine restarted and needs to
 * re-read files) or apply without reloading (stage changes for a
 * maintenance window).
 *
 * <p>Current backend behaviour: records an audit entry and returns
 * success. Does NOT yet reach the engine because there's no HTTP reload
 * endpoint upstream — see the {@code ConfigReloadController} javadoc.
 */
export function useReloadConfig() {
  const qc = useQueryClient();
  return useMutation<ReloadConfigResponse, Error, { name: string }>({
    mutationFn: ({ name }) => configApi.reload(name),
    onSuccess: (_data, variables) => {
      // When real reload lands, the engine-side state may have changed —
      // invalidate channel caches so the UI re-polls.
      if (variables.name === 'channel.conf') {
        qc.invalidateQueries({ queryKey: ['channels'] });
        qc.invalidateQueries({ queryKey: ['channel-detail'] });
      }
    },
  });
}

/**
 * Full engine restart — stop + start, distinct from the graceful reload.
 *
 * <p>Currently a stub on the backend (returns {@code engineRestarted: false});
 * UI can exercise the full flow including banners and audit entries.
 * The real restart mechanism is pending design (jsocket.sh restart vs JMX
 * vs systemd). When it lands, no FE change is needed.
 */
export function useRestartConfig() {
  const qc = useQueryClient();
  return useMutation<RestartConfigResponse, Error, { name: string }>({
    mutationFn: ({ name }) => configApi.restart(name),
    onSuccess: (_data) => {
      // A real restart invalidates essentially everything that reads engine
      // state — engine health, channel state, JVM snapshots, network metrics.
      // When the stub is replaced with a real restart we'll want all of
      // these to refresh. Keeping them invalidated now is harmless (stub
      // does no actual work so the refresh sees the same state).
      qc.invalidateQueries({ queryKey: ['channels'] });
      qc.invalidateQueries({ queryKey: ['channel-detail'] });
      qc.invalidateQueries({ queryKey: ['engine-health'] });
      qc.invalidateQueries({ queryKey: ['jvm'] });
      qc.invalidateQueries({ queryKey: ['network'] });
    },
  });
}
