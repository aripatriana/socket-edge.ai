import type { NetworkInterfaceInfo } from '../../api/network.types';

/**
 * Interface selector. Rendered as pill group above the bandwidth chart.
 * Clicking a pill switches the chart target. Down interfaces are dimmed
 * but still selectable (operator may want to see an interface that
 * recently went down).
 */
export function InterfaceSelector({
  interfaces,
  selected,
  onSelect,
}: {
  interfaces: NetworkInterfaceInfo[];
  selected: string | null;
  onSelect: (name: string) => void;
}) {
  if (interfaces.length === 0) return null;

  return (
    <div className="flex items-center gap-2 flex-wrap">
      <span className="text-[11px] uppercase tracking-wider font-semibold text-muted-foreground">
        Interface
      </span>
      <div className="inline-flex rounded border border-border overflow-hidden">
        {interfaces.map((iface, idx) => {
          const isSelected = iface.name === selected;
          const divider = idx > 0;
          return (
            <button
              key={iface.name}
              type="button"
              onClick={() => onSelect(iface.name)}
              title={iface.displayName}
              className={[
                'px-3 py-1 text-[12px] font-mono cursor-pointer transition-colors',
                divider ? 'border-l border-border' : '',
                isSelected
                  ? 'bg-foreground text-background'
                  : 'bg-card text-muted-foreground hover:bg-muted hover:text-foreground',
                !iface.up ? 'opacity-60' : '',
              ].join(' ')}
            >
              {iface.name}
              {!iface.up && <span className="ml-1 text-[9px]">●DOWN</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}
