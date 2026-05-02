import { useEffect, useMemo } from 'react';
import { useParams, useSearchParams, useNavigate, Navigate } from 'react-router-dom';
import { FileText, History } from 'lucide-react';
import { AppShell } from '../../components/layout/AppShell';
import { EditorView } from '../../components/config/EditorView';
import { HistoryView } from '../../components/configHistory/HistoryView';
import { useConfigFiles } from '../../hooks/useConfig';

/**
 * Configuration page for a single file.
 *
 * <p>Each Configuration menu entry (Channel, System, Cluster) resolves to
 * {@code /config/{fileName}} and shows one file. We used to render a
 * {@code ConfigFilePanel} on the left — a mini file tree — but once the
 * sidebar became a single-file filter it duplicated the top-level menu
 * and ate ~260px of horizontal space that the editor needs. The panel has
 * been removed. The top menu + page header carry the "which file" context.
 *
 * <p>If a future menu (e.g. Profiles) legitimately groups multiple files,
 * re-introduce {@code ConfigFilePanel} only for that menu rather than
 * forcing it on single-file pages.
 *
 * <p>URL shape:
 * <pre>
 *   /config                                         → redirect to channel.conf + editor
 *   /config/{fileName}?view=editor                  → editor view
 *   /config/{fileName}?view=history                 → history view
 *   /config/{fileName}?view=history&v={N}           → history, version N selected
 * </pre>
 *
 * <p>Chat 3c-1h-fix: every hook is called unconditionally at the top of
 * the function body. Returning early before {@code useEffect} caused
 * React error #310 ("Rendered more hooks than during the previous render").
 */
export function ConfigPage() {
  // --- Hooks (unconditional, always in the same order) -----------------
  const { fileName } = useParams<{ fileName: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  // We still fetch the file list — it powers the whitelist guard below
  // (cleaner than letting useConfigFile surface a 404).
  const { data, isLoading, isError } = useConfigFiles();
  const allFiles = data?.files ?? [];

  const rawView = searchParams.get('view');
  const view: 'editor' | 'history' =
    rawView === 'history' ? 'history' : 'editor';
  const selectedVersion = useMemo(() => {
    const raw = searchParams.get('v');
    return raw === null ? null : Number(raw);
  }, [searchParams]);

  // Normalise bogus ?view= values — always running this hook keeps the
  // call order stable across renders.
  useEffect(() => {
    if (rawView !== null && rawView !== 'editor' && rawView !== 'history') {
      const next = new URLSearchParams(searchParams);
      next.set('view', 'editor');
      setSearchParams(next, { replace: true });
    }
  }, [rawView, searchParams, setSearchParams]);

  // --- Non-hook early exits (safe because all hooks are above) ---------

  if (!fileName) {
    if (isLoading) {
      return (
        <AppShell>
          <div className="flex items-center justify-center h-[300px] text-[12px] text-muted-foreground">
            Loading…
          </div>
        </AppShell>
      );
    }
    // Bare /config — land on Channel as a sensible default, keeping old
    // bookmarks working without dropping the back-compat /config route.
    return <Navigate to="/config/channel.conf?view=editor" replace />;
  }

  // Whitelist guard: the backend enforces this too, but catching it in
  // the URL routing gives a cleaner experience than an opaque 404 from
  // useConfigFile(). We only run this check once the files list is
  // loaded — during initial load we trust the URL and let EditorView
  // handle any downstream error.
  const fileIsKnown = allFiles.some((f) => f.name === fileName);
  if (!isLoading && allFiles.length > 0 && !fileIsKnown) {
    return (
      <AppShell>
        <div className="flex-1 grid place-items-center p-6">
          <div className="rounded-md border border-dashed border-border bg-muted/20 px-8 py-10 text-center text-[13px] text-muted-foreground max-w-md">
            <div className="font-semibold text-foreground mb-2">
              Unknown configuration file
            </div>
            <div className="text-[11px] font-mono">
              <span className="text-foreground">{fileName}</span> is not in the
              server whitelist.
            </div>
          </div>
        </div>
      </AppShell>
    );
  }

  // --- Handlers --------------------------------------------------------

  const switchView = (nextView: 'editor' | 'history') => {
    const next = new URLSearchParams(searchParams);
    next.set('view', nextView);
    next.delete('v'); // version is history-only
    setSearchParams(next);
  };

  const selectVersion = (v: number | null) => {
    const next = new URLSearchParams(searchParams);
    if (v === null) next.delete('v');
    else next.set('v', String(v));
    setSearchParams(next);
  };

  // Kept for any future component that wants to jump to a sibling file
  // (e.g. a "related files" card). The sidebar no longer uses it.
  const selectFile = (name: string) => {
    const next = new URLSearchParams();
    next.set('view', view);
    navigate(`/config/${encodeURIComponent(name)}?${next.toString()}`);
  };
  void selectFile; // silence unused-var in strict builds

  // --- Render ----------------------------------------------------------

  return (
    <AppShell>
      <div className="flex h-full -m-5" style={{ height: 'calc(100vh - 52px)' }}>
        <section className="flex-1 flex flex-col overflow-hidden bg-background">
          {isError && (
            <div className="m-4 rounded-md border border-destructive bg-destructive/10 p-4 text-[13px]">
              <div className="font-semibold text-destructive">
                Failed to load configuration files
              </div>
              <div className="text-[11px] text-muted-foreground mt-1 font-mono">
                The /api/config/files endpoint returned an error.
              </div>
            </div>
          )}

          <ViewTabs
            fileName={fileName}
            activeView={view}
            onChange={switchView}
          />

          {view === 'editor' && <EditorView fileName={fileName} />}
          {view === 'history' && (
            <HistoryView
              fileName={fileName}
              selectedVersion={selectedVersion}
              onSelectVersion={selectVersion}
            />
          )}
        </section>
      </div>
    </AppShell>
  );
}

// ---------------------------------------------------------------------------

function ViewTabs({
  fileName,
  activeView,
  onChange,
}: {
  fileName: string;
  activeView: 'editor' | 'history';
  onChange: (v: 'editor' | 'history') => void;
}) {
  return (
    <header className="flex items-center gap-0 px-4 border-b border-border bg-card">
      <div className="py-3 mr-5">
        <div className="font-mono font-semibold text-[14px] text-foreground">
          {fileName}
        </div>
      </div>

      <nav role="tablist" className="flex items-center gap-0">
        <ViewTab
          label="Editor"
          icon={<FileText className="w-3.5 h-3.5" />}
          active={activeView === 'editor'}
          onClick={() => onChange('editor')}
        />
        <ViewTab
          label="History"
          icon={<History className="w-3.5 h-3.5" />}
          active={activeView === 'history'}
          onClick={() => onChange('history')}
        />
      </nav>

      <div className="flex-1" />
    </header>
  );
}

function ViewTab({
  label,
  icon,
  active,
  onClick,
}: {
  label: string;
  icon: React.ReactNode;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={[
        'relative flex items-center gap-1.5 px-4 py-3 text-[13px] transition-colors',
        active
          ? 'text-foreground font-semibold'
          : 'text-muted-foreground hover:text-foreground',
      ].join(' ')}
    >
      {icon}
      <span>{label}</span>
      {active && (
        <span className="absolute bottom-[-1px] left-0 right-0 h-[2px] bg-primary" />
      )}
    </button>
  );
}
