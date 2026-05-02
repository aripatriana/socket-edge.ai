import { NavLink } from 'react-router-dom';

/**
 * Horizontal tab navigation for section-level layouts (Monitoring, Config).
 * Each tab is a NavLink — React Router handles active state automatically.
 *
 * Tabs can be disabled ("coming soon") to preview the full layout before
 * each tab is built. Disabled tabs render with muted styling and no link.
 */
export interface SubTab {
  to?: string;
  label: string;
  icon?: string;       // optional glyph
  disabled?: boolean;
}

export function SubTabs({ tabs }: { tabs: SubTab[] }) {
  return (
    <nav className="flex gap-0.5 mb-4 bg-card rounded-t-md border border-b-0 border-border px-1">
      {tabs.map((tab) => (
        <SubTabItem key={tab.label} tab={tab} />
      ))}
    </nav>
  );
}

function SubTabItem({ tab }: { tab: SubTab }) {
  if (tab.disabled || !tab.to) {
    return (
      <div className="px-4 py-2.5 text-[13px] text-muted-foreground cursor-not-allowed flex items-center gap-2">
        {tab.icon && <span className="opacity-60">{tab.icon}</span>}
        <span>{tab.label}</span>
        <span className="text-[9px] uppercase tracking-wider opacity-60">soon</span>
      </div>
    );
  }

  return (
    <NavLink
      to={tab.to}
      end
      className={({ isActive }) =>
        [
          'px-4 py-2.5 text-[13px] no-underline border-b-2 flex items-center gap-2 transition-colors',
          isActive
            ? 'text-primary border-primary font-semibold'
            : 'text-muted-foreground border-transparent hover:text-foreground',
        ].join(' ')
      }
    >
      {tab.icon && <span>{tab.icon}</span>}
      <span>{tab.label}</span>
    </NavLink>
  );
}
