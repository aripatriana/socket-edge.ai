import { FileText, FileCode, ScrollText } from 'lucide-react';
import type { ConfigFileRef } from '../../api/config.types';

interface Props {
  files: ConfigFileRef[];
  selectedName: string | null;
  dirtyNames: Set<string>;
  onSelect: (name: string) => void;
}

/**
 * Left-side file panel for the Config Editor.
 *
 * <p>Files are grouped by their {@code section} field ("Core", "Profiles",
 * "Logging") — the server decides grouping so new files can be added
 * without a frontend change.
 *
 * <p>A modified indicator (dot) next to the filename marks files with
 * unsaved local edits — the {@code dirtyNames} set is maintained by the
 * parent page across file switches.
 */
export function ConfigFilePanel({ files, selectedName, dirtyNames, onSelect }: Props) {
  // Preserve section ordering from server; use Map to get stable group order.
  const grouped = new Map<string, ConfigFileRef[]>();
  for (const f of files) {
    const key = f.section || 'Other';
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key)!.push(f);
  }

  return (
    <aside className="flex flex-col overflow-hidden bg-card border-r border-border" style={{ width: 260 }}>
      <header className="px-4 py-3 border-b border-border">
        <div className="text-[13px] font-semibold text-foreground">Configuration Files</div>
        <div className="text-[11px] text-muted-foreground mt-0.5 font-mono">/conf/</div>
      </header>

      <div className="flex-1 overflow-y-auto py-2">
        {files.length === 0 && (
          <div className="px-4 py-3 text-[12px] italic text-muted-foreground">
            No config files found.
          </div>
        )}
        {Array.from(grouped.entries()).map(([section, group]) => (
          <div key={section}>
            <div className="text-[10px] uppercase tracking-wider font-semibold px-4 pt-3 pb-1 text-muted-foreground">
              {section}
            </div>
            {group.map((f) => (
              <FileRow
                key={f.name}
                file={f}
                selected={f.name === selectedName}
                dirty={dirtyNames.has(f.name)}
                onClick={() => onSelect(f.name)}
              />
            ))}
          </div>
        ))}
      </div>
    </aside>
  );
}

function FileRow({
  file,
  selected,
  dirty,
  onClick,
}: {
  file: ConfigFileRef;
  selected: boolean;
  dirty: boolean;
  onClick: () => void;
}) {
  const Icon = pickIcon(file.name);
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        'w-full flex items-center gap-2 px-4 py-1.5 text-left text-[12px] transition-colors border-l-2',
        selected
          ? 'bg-muted/50 border-primary text-foreground'
          : 'border-transparent text-foreground hover:bg-muted/30',
      ].join(' ')}
    >
      <Icon className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
      <span className="font-mono truncate flex-1">{file.name}</span>
      {dirty && (
        <span
          className="inline-block w-1.5 h-1.5 rounded-full bg-primary"
          title="Unsaved changes"
        />
      )}
      {!dirty && (
        <span className="font-mono text-[10px] text-muted-foreground">
          v{file.currentVersion}
        </span>
      )}
    </button>
  );
}

function pickIcon(name: string): React.ComponentType<{ className?: string }> {
  if (name.endsWith('.profile')) return FileCode;
  if (name.endsWith('.xml')) return ScrollText;
  return FileText;
}
