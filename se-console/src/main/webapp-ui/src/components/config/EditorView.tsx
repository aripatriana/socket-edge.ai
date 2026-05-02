import { useEffect, useState } from 'react';
import { ConfigEditor } from './ConfigEditor';
import { EditorToolbar } from './EditorToolbar';
import { ValidationPanel } from './ValidationPanel';
import {
  useConfigFile,
  useValidateConfig,
  useApplyConfig,
  useReloadConfig,
  useRestartConfig,
} from '../../hooks/useConfig';
import type { ValidateResponse } from '../../api/config.types';
import { ApiError } from '../../api/client';

interface Props {
  fileName: string;
}

/**
 * Decide which post-apply action a file exposes. Separated so the rule
 * lives in one place, easy to audit/change.
 *
 * <ul>
 *   <li>{@code channel.conf} → Reload. The engine supports hot reload of
 *       channel definitions with zero downtime.</li>
 *   <li>{@code system.conf}, {@code cluster.conf} → Restart. These files
 *       govern server-level and cluster-level state that the engine
 *       cannot re-read without a full cold boot.</li>
 * </ul>
 */
function postApplyActionFor(fileName: string): 'reload' | 'restart' {
  if (fileName === 'system.conf' || fileName === 'cluster.conf') return 'restart';
  return 'reload';
}

/**
 * Editor content for a single config file. Shell + file panel live in the
 * parent ConfigPage; this component owns the edit / validate / apply /
 * reload / restart lifecycle for the file it's given.
 *
 * <p>The toolbar exposes three actions plus one of Reload/Restart
 * depending on {@link #postApplyActionFor} — so operators never see
 * both. Reload and Restart have distinct semantics and exposing the
 * wrong one per file is an accident waiting to happen.
 *
 * <p>Both mutations are still declared here (the unused one just never
 * fires) so the hook order stays stable regardless of which file is
 * being edited. Conditionally declaring hooks would break React's rules.
 *
 * <p>Draft state is keyed by fileName so the parent can stay mounted while
 * the user switches files via the panel.
 */
export function EditorView({ fileName }: Props) {
  const { data: file, isLoading, isError, error } = useConfigFile(fileName);

  // Draft content keyed to the current file — resets when fileName changes.
  const [draft, setDraft] = useState<string | null>(null);
  const [validationResult, setValidationResult] = useState<ValidateResponse | null>(null);
  const [banner, setBanner] = useState<BannerState | null>(null);

  useEffect(() => {
    setDraft(null);
    setValidationResult(null);
  }, [fileName]);

  const currentContent = draft ?? file?.content ?? '';
  const isDirty = !!file && currentContent !== file.content;

  const validateMut = useValidateConfig();
  const applyMut = useApplyConfig();
  const reloadMut = useReloadConfig();
  const restartMut = useRestartConfig();

  useEffect(() => {
    if (validationResult) setValidationResult(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentContent]);

  const canApply = !!validationResult && validationResult.valid;
  const postApplyAction = postApplyActionFor(fileName);

  const onChangeContent = (next: string) => setDraft(next);

  const onDiscard = () => {
    if (!file) return;
    setDraft(null);
    setValidationResult(null);
  };

  const onValidate = async () => {
    try {
      const res = await validateMut.mutateAsync({
        name: fileName,
        content: currentContent,
      });
      setValidationResult(res);
    } catch (err) {
      setBanner({
        kind: 'error',
        message: 'Validation request failed: ' + extractError(err),
      });
    }
  };

  const onApply = async () => {
    if (!file) return;
    if (!validationResult || !validationResult.valid) {
      setBanner({
        kind: 'error',
        message: 'Run Validate first — Apply is disabled until validation passes.',
      });
      return;
    }
    try {
      const res = await applyMut.mutateAsync({
        name: fileName,
        content: currentContent,
        expectedVersion: file.currentVersion,
      });
      setDraft(null);
      setValidationResult(null);
      setBanner({
        kind: 'success',
        message: `Applied ${fileName} as v${res.newVersion} (${res.totalMs}ms). `
          + (postApplyAction === 'reload'
            ? 'Engine not reloaded yet — click Reload when ready.'
            : 'Engine not restarted yet — click Restart when ready.'),
      });
    } catch (err) {
      setBanner({
        kind: 'error',
        message: 'Apply failed: ' + extractError(err),
      });
    }
  };

  const onReload = async () => {
    if (!validationResult || !validationResult.valid) {
      setBanner({
        kind: 'error',
        message: 'Run Validate first — Reload is disabled until validation passes.',
      });
      return;
    }
    try {
      const res = await reloadMut.mutateAsync({ name: fileName });
      setBanner({
        kind: res.engineReloaded ? 'success' : 'info',
        message: res.engineReloaded
          ? `Engine reloaded ${fileName} in ${res.durationMs}ms.`
          : `Reload requested (${res.durationMs}ms). ${res.message}`,
      });
    } catch (err) {
      setBanner({
        kind: 'error',
        message: 'Reload failed: ' + extractError(err),
      });
    }
  };

  const onRestart = async () => {
    if (!validationResult || !validationResult.valid) {
      setBanner({
        kind: 'error',
        message: 'Run Validate first — Restart is disabled until validation passes.',
      });
      return;
    }
    // Belt-and-braces confirmation on top of the destructive-tone button:
    // restart drops all TCP connections, so we want the operator to
    // actively assent rather than dismiss a subtle red button.
    const confirmed = window.confirm(
      `Restart the engine now?\n\n`
        + `This will STOP and START the engine process. All active TCP\n`
        + `connections will drop and inflight messages will be lost.\n\n`
        + `File: ${fileName}\n\n`
        + `Proceed?`,
    );
    if (!confirmed) return;
    try {
      const res = await restartMut.mutateAsync({ name: fileName });
      setBanner({
        kind: res.engineRestarted ? 'success' : 'info',
        message: res.engineRestarted
          ? `Engine restarted successfully in ${res.durationMs}ms.`
          : `Restart requested (${res.durationMs}ms). ${res.message}`,
      });
    } catch (err) {
      setBanner({
        kind: 'error',
        message: 'Restart failed: ' + extractError(err),
      });
    }
  };

  // beforeunload warning when dirty
  useEffect(() => {
    if (!isDirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [isDirty]);

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {banner && (
        <InlineBanner state={banner} onDismiss={() => setBanner(null)} />
      )}

      {isError && (
        <div className="m-4 rounded-md border border-destructive bg-destructive/10 p-4">
          <div className="text-[13px] font-semibold text-destructive">
            Failed to load {fileName}
          </div>
          <div className="text-[11px] text-muted-foreground mt-1 font-mono">
            {error instanceof Error ? error.message : String(error)}
          </div>
        </div>
      )}

      {isLoading && !file && (
        <div className="flex-1 grid place-items-center text-[12px] text-muted-foreground">
          Loading {fileName}…
        </div>
      )}

      {file && (
        <>
          <EditorToolbar
            file={file}
            isDirty={isDirty}
            isValidating={validateMut.isPending}
            isApplying={applyMut.isPending}
            isReloading={reloadMut.isPending}
            isRestarting={restartMut.isPending}
            canApply={canApply}
            postApplyAction={postApplyAction}
            onValidate={onValidate}
            onApply={onApply}
            onReload={onReload}
            onRestart={onRestart}
            onDiscard={onDiscard}
          />
          <div className="flex-1 overflow-hidden">
            <ConfigEditor
              content={currentContent}
              onChange={onChangeContent}
              readOnly={applyMut.isPending}
              validationMessages={
                validationResult
                  ? [...validationResult.errors, ...validationResult.warnings]
                  : undefined
              }
            />
          </div>
          <ValidationPanel
            result={validationResult}
            isValidating={validateMut.isPending}
          />
        </>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------

interface BannerState {
  kind: 'success' | 'error' | 'info';
  message: string;
}

function InlineBanner({ state, onDismiss }: { state: BannerState; onDismiss: () => void }) {
  const tone =
    state.kind === 'success'
      ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
      : state.kind === 'info'
        ? 'bg-sky-50 border-sky-200 text-sky-800'
        : 'bg-red-50 border-red-200 text-red-800';
  return (
    <div role="status" className={`flex items-start gap-2 border-b px-4 py-2 text-[12px] ${tone}`}>
      <span className="flex-1 font-mono break-all">{state.message}</span>
      <button
        type="button"
        onClick={onDismiss}
        className="opacity-60 hover:opacity-100 transition-opacity text-[14px]"
        aria-label="Dismiss"
      >
        ×
      </button>
    </div>
  );
}

function extractError(err: unknown): string {
  if (err instanceof ApiError) {
    const body = err.details as Record<string, unknown> | undefined;
    if (body?.message && typeof body.message === 'string') return body.message;
    return err.message;
  }
  if (err instanceof Error) return err.message;
  return String(err);
}
