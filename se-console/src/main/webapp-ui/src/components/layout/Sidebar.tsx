import { NavLink } from 'react-router-dom';

/**
 * Left sidebar. Dark navy surface, brand header, section headers + nav links.
 * Uses hardcoded dark colors (slate-900 family) since admin dashboards
 * conventionally keep the sidebar dark for separation, independent of the
 * light/dark theme of the main content.
 *
 * Active nav item is marked with the primary color (Jalin red) left border.
 */
interface NavItem {
  to?: string;
  label: string;
  icon: string;
  disabled?: boolean;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

const SECTIONS: NavSection[] = [
  {
    title: 'Overview',
    items: [{ to: '/', label: 'Dashboard', icon: '▦' }],
  },
  {
    title: 'Engine',
    items: [
      { label: 'Channels', icon: '⇆', disabled: true },
      { label: 'Monitoring', icon: '◉', disabled: true },
      { label: 'Logs', icon: '≡', disabled: true },
    ],
  },
  {
    title: 'Configuration',
    items: [
      { label: 'Editor', icon: '✎', disabled: true },
      { label: 'History', icon: '⟳', disabled: true },
    ],
  },
  {
    title: 'Admin',
    items: [
      { label: 'Audit Trail', icon: '⎆', disabled: true },
      { label: 'Users', icon: '◎', disabled: true },
      { label: 'Settings', icon: '⚙', disabled: true },
    ],
  },
];

export function Sidebar() {
  return (
    <aside
      className="flex flex-col overflow-hidden bg-slate-900 text-slate-300"
      style={{ gridArea: 'sidebar' }}
    >
      {/* Brand */}
      <div className="flex items-center gap-3 px-5 h-[52px] border-b border-slate-800">
        <div className="w-7 h-7 rounded flex items-center justify-center font-bold text-sm text-primary-foreground bg-primary">
          J
        </div>
        <div className="flex flex-col leading-tight">
          <span className="text-white font-semibold text-[13px]">SE-Console</span>
          <span className="text-[10px] uppercase tracking-wider text-slate-500">
            Socket Edge
          </span>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto py-3">
        {SECTIONS.map((section) => (
          <div key={section.title}>
            <div className="text-[10px] uppercase tracking-wider font-semibold px-5 pt-4 pb-1 text-slate-500">
              {section.title}
            </div>
            {section.items.map((item) => (
              <NavItemRow key={item.label} item={item} />
            ))}
          </div>
        ))}
      </nav>

      {/* Footer */}
      <div className="px-5 py-3 text-[11px] border-t border-slate-800 text-slate-500">
        <div className="font-mono">v0.3.0</div>
        <div className="mt-1">System metrics</div>
      </div>
    </aside>
  );
}

function NavItemRow({ item }: { item: NavItem }) {
  if (item.disabled || !item.to) {
    return (
      <div
        className="flex items-center gap-3 px-5 py-2 text-[13px] border-l-2 border-transparent cursor-not-allowed text-slate-600"
      >
        <span className="w-4 opacity-60">{item.icon}</span>
        <span>{item.label}</span>
        <span className="ml-auto text-[9px] uppercase tracking-wider opacity-60">
          soon
        </span>
      </div>
    );
  }

  return (
    <NavLink
      to={item.to}
      end={item.to === '/'}
      className={({ isActive }) =>
        [
          'flex items-center gap-3 px-5 py-2 text-[13px] border-l-2 no-underline transition-colors',
          isActive
            ? 'bg-slate-800 text-white border-primary'
            : 'border-transparent text-slate-300 hover:bg-slate-800 hover:text-white',
        ].join(' ')
      }
    >
      <span className="w-4">{item.icon}</span>
      <span>{item.label}</span>
    </NavLink>
  );
}
