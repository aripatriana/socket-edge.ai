import { useEffect, useMemo, useState } from 'react';
import { RotateCcw } from 'lucide-react';
import { VersionTimeline } from './VersionTimeline';
import { DiffView } from './DiffView';
import { RollbackDialog } from './RollbackDialog';
import {
  useConfigHistory,
  useConfigDiff,
  useConfigRollback,
} from '../../hooks/useConfigHistory';
import { ApiError } from '../../api/client';
import type { ConfigVersionSummary } from '../../api/configHistory.types';

interface Props {
  fileName: string;
  selectedVersion: number | null;
  onSelectVersion: (version: number | null) => void;
}

/**
 * History content for a single config file. Shell + file panel + tab bar
 * live in the parent ConfigPage; this component owns the timeline + diff
 * + rollback lifecycle.
 *
 * <p>{@code selectedVersion} is lifted to the parent so it survives in the
 * URL — refreshing or linking to {@code ?view=history&v=3} lands on the
 * same diff view.
 */
export function HistoryView({ fileName, selectedVersion, onSelectVersion }: Props) {
  const {
    data: history,
    isLoading,
    isError,
    error,
  } = useConfigHistory(fileName);

  const currentVersion = history?.currentVersion ?? null;
  const versions: ConfigVersionSummary[] = history?.versions ?? [];

  // Default selection: newest non-current version → meaningful diff on load.
  useEffect(() => {
    if (selectedVersion !== null) return;
    if (versions.length === 0) return;
    const fallback = versions.length > 1 ? versions[1].version : versions[0].version;
    onSelectVersion(fallback);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [versions.length]);

  // Diff query — only fires when v1 !== v2
  const diffQuery = useConfigDiff(fileName, currentVersion, selectedVersion);
  const diff = diffQuery.data;

  // Rollback
  const rollback = useConfigRollback();
  const [rollbackTarget, setRollbackTarget] = useState<ConfigVersionSummary | null>(null);
  const [banner, setBanner] = useState<Banner | null>(null);

  const handleRollback = async () => {
    if (!rollbackTarget) return;
    try {
      const res = await rollback.mutateAsync({
        fileName,
        version: rollbackTarget.version,
      });
      setBanner({
        kind: 'success',
        message: `Rolled back ${fileName} to v${rollbackTarget.version}. New version: v${res.newVersion}.`,
      });
      setRollbackTarget(null);
      onSelectVersion(null); // next useEffect will pick the new newest-non-current
    } catch (err) {
      setBanner({
        kind: 'error',
        message: 'Rollback failed: ' + extractError(err),
      });
    }
  };

  const selectedVersionDetail = useMemo(
    () => versions.find((v) => v.version === selectedVersion) ?? null,
    [versions, selectedVersion],
  );

  const showRollback =
    selectedVersionDetail &&
    currentVersion !== null &&
    selectedVersion !== null &&
    selectedVersion !== currentVersion;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {banner && <InlineBanner state={banner} onDismiss={() => setBanner(null)} />}

      {/* Sub-header row — metadata + rollback button */}
      <header className="flex items-center gap-3 px-4 py-2 border-b border-border bg-muted/20 flex-wrap">
        <div className="text-[11px] font-mono text-muted-foreground">
          {versions.length} version{versions.length === 1 ? '' : 's'}
          {currentVersion !== null && (
            <>
              {' · current '}
              <span className="text-foreground">v{currentVersion}</span>
            </>
          )}
          {selectedVersion !== null && selectedVersion !== currentVersion && (
            <>
              {' · comparing with '}
              <span className="text-foreground">v{selectedVersion}</span>
            </>
          )}
        </div>

        <div className="flex-1" />

        {showRollback && (
          <button
            type="button"
            onClick={() => setRollbackTarget(selectedVersionDetail)}
            disabled={rollback.isPending}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] rounded border border-amber-300 bg-amber-50 text-amber-800 hover:bg-amber-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Rollback to v{selectedVersion}</span>
          </button>
        )}
      </header>

      {isError && (
        <div className="m-4 rounded-md border border-destructive bg-destructive/10 p-4">
          <div className="text-[13px] font-semibold text-destructive">
            Failed to load history
          </div>
          <div className="text-[11px] text-muted-foreground mt-1 font-mono">
            {error instanceof Error ? error.message : String(error)}
          </div>
        </div>
      )}

      <div className="flex-1 flex overflow-hidden">
        <VersionTimeline
          versions={versions}
          currentVersion={currentVersion}
          selectedVersion={selectedVersion}
          onSelect={onSelectVersion}
        />

        <section className="flex-1 flex flex-col overflow-hidden bg-card">
          {!isError && isLoading && (
            <div className="flex-1 grid place-items-center text-[12px] text-muted-foreground">
              Loading history…
            </div>
          )}

          {!isLoading && !isError && versions.length === 0 && (
            <Empty message="No history yet. Apply a config change first." />
          )}

          {diffQuery.isLoading && (
            <div className="flex-1 grid place-items-center text-[12px] text-muted-foreground">
              Computing diff…
            </div>
          )}

          {diff &&
            currentVersion !== null &&
            selectedVersion !== null &&
            selectedVersion !== currentVersion && (
            <DiffView
              v1Content={diff.v2Content}
              v2Content={diff.v1Content}
              v1Label={`v${selectedVersion}`}
              v2Label={`v${currentVersion} (current)`}
            />
          )}

          {currentVersion !== null && selectedVersion === currentVersion && (
            <div className="flex-1 grid place-items-center text-[12px] italic text-muted-foreground p-8">
              v{currentVersion} is the current version — nothing to diff against.
              Select an older version to compare.
            </div>
          )}
        </section>
      </div>

      <RollbackDialog
        targetVersion={rollbackTarget}
        currentVersion={currentVersion}
        fileName={fileName}
        busy={rollback.isPending}
        onCancel={() => setRollbackTarget(null)}
        onConfirm={handleRollback}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------

interface Banner {
  kind: 'success' | 'error';
  message: string;
}

function InlineBanner({ state, onDismiss }: { state: Banner; onDismiss: () => void }) {
  const tone = state.kind === 'success'
    ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
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

function Empty({ message }: { message: string }) {
  return (
    <div className="flex-1 grid place-items-center p-6">
      <div className="rounded-md border border-dashed border-border bg-muted/20 px-8 py-10 text-center text-[13px] text-muted-foreground">
        {message}
      </div>
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
