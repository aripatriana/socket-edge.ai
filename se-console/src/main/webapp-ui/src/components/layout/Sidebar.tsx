import { NavLink, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';

interface NavItem {
  to?: string;
  label: string;
  icon: string;
  disabled?: boolean;
  adminOnly?: boolean;
  activeMatch?: (pathname: string) => boolean;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

function matchesConfigFile(expectedFileName: string) {
  return (pathname: string) => {
    const match = pathname.match(/^\/config\/([^/]+)/);
    if (!match) return false;
    try {
      return decodeURIComponent(match[1]) === expectedFileName;
    } catch {
      return match[1] === expectedFileName;
    }
  };
}

/**
 * Navigation sections. Structure mirrors the operational concerns an
 * operator has: what's happening now (Overview, Engine), what to
 * configure (Configuration), and what admins manage (Admin).
 *
 * <p><b>No Settings menu.</b> Following the NGINX Plus dashboard
 * convention — global admin settings do not live in the sidebar.
 * Editable engine behaviour belongs in {@code system.conf} /
 * {@code cluster.conf} (already present under Configuration).
 * Security/auth policy lives in {@code application.yml} and requires
 * a restart to change, which is intentional. Per-page concerns like
 * polling intervals can, if ever needed, sit as contextual gear icons
 * on the relevant page rather than a global menu.
 *
 * <p>If a genuine use case for runtime-editable global settings
 * emerges later, re-add as a single focused page for that use case —
 * not as a grab-bag "Settings" section.
 */
const SECTIONS: NavSection[] = [
  {
    title: 'Overview',
    items: [{ to: '/', label: 'Dashboard', icon: '▦' }],
  },
  {
    // "Observability" rather than "Engine" because the items below are
    // about *watching* the engine (channels live state, monitoring,
    // logs), not about the engine itself. The engine's own
    // configuration lives under Configuration. This rename keeps the
    // industry convention (Grafana, Kibana, Datadog all group live
    // state + metrics + logs under an observability heading).
    title: 'Observability',
    items: [
      {
        to: '/channels',
        label: 'Channels',
        icon: '⇆',
        activeMatch: (p) => p.startsWith('/channels'),
      },
      {
        to: '/monitoring/system',
        label: 'Monitoring',
        icon: '◉',
        activeMatch: (p) => p.startsWith('/monitoring'),
      },
      {
        to: '/logs',
        label: 'Logs',
        icon: '≡',
        activeMatch: (p) => p.startsWith('/logs'),
      },
    ],
  },
  {
    title: 'Configuration',
    items: [
      {
        to: '/config/channel.conf',
        label: 'Channel',
        icon: '⇆',
        activeMatch: matchesConfigFile('channel.conf'),
      },
      {
        to: '/config/system.conf',
        label: 'System',
        icon: '⚙',
        activeMatch: matchesConfigFile('system.conf'),
      },
      {
        to: '/config/cluster.conf',
        label: 'Cluster',
        icon: '⎇',
        activeMatch: matchesConfigFile('cluster.conf'),
      },
    ],
  },
  {
    title: 'Admin',
    items: [
      {
        to: '/audit',
        label: 'Audit Trail',
        icon: '⎆',
        adminOnly: true,
        activeMatch: (p) => p.startsWith('/audit'),
      },
      {
        to: '/users',
        label: 'Users',
        icon: '◎',
        adminOnly: true,
        activeMatch: (p) => p.startsWith('/users'),
      },
    ],
  },
];

export function Sidebar() {
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === 'admin';

  // Drop admin-only items for non-admins, then drop empty sections —
  // a non-admin seeing "Admin" as a header with no items below would
  // look broken. For admins, the Admin section shows Audit + Users.
  const visibleSections = SECTIONS
    .map((s) => ({
      ...s,
      items: s.items.filter((item) => !item.adminOnly || isAdmin),
    }))
    .filter((s) => s.items.length > 0);

  return (
    <aside
      className="flex flex-col overflow-hidden bg-slate-900 text-slate-300"
      style={{ gridArea: 'sidebar' }}
    >
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

      <nav className="flex-1 overflow-y-auto py-3">
        {visibleSections.map((section) => (
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

      <div className="px-5 py-3 text-[11px] border-t border-slate-800 text-slate-500">
        <div className="font-mono">v0.8.1</div>
        <div className="mt-1">System deep-dive</div>
      </div>
    </aside>
  );
}

function NavItemRow({ item }: { item: NavItem }) {
  const location = useLocation();

  if (item.disabled || !item.to) {
    return (
      <div className="flex items-center gap-3 px-5 py-2 text-[13px] border-l-2 border-transparent cursor-not-allowed text-slate-600">
        <span className="w-4 opacity-60">{item.icon}</span>
        <span>{item.label}</span>
        <span className="ml-auto text-[9px] uppercase tracking-wider opacity-60">
          soon
        </span>
      </div>
    );
  }

  const customActive = item.activeMatch ? item.activeMatch(location.pathname) : undefined;

  return (
    <NavLink
      to={item.to}
      end={item.to === '/' && !item.activeMatch}
      className={({ isActive }) => {
        const active = customActive ?? isActive;
        return [
          'flex items-center gap-3 px-5 py-2 text-[13px] border-l-2 no-underline transition-colors',
          active
            ? 'bg-slate-800 text-white border-primary'
            : 'border-transparent text-slate-300 hover:bg-slate-800 hover:text-white',
        ].join(' ');
      }}
    >
      <span className="w-4">{item.icon}</span>
      <span>{item.label}</span>
    </NavLink>
  );
}
