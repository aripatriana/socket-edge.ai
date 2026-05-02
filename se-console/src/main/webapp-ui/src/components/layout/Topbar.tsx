import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import { authApi } from '../../api/auth';
import { EngineHealthBadge } from '../channels/EngineHealthBadge';

export function Topbar({ lastUpdated }: { lastUpdated?: Date | null }) {
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);

  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    function onDocClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [menuOpen]);

  async function handleLogout() {
    try {
      await authApi.logout();
    } catch {
      // swallow
    }
    clear();
    navigate('/login', { replace: true });
  }

  const breadcrumb = routeLabel(location.pathname, location.search);

  return (
    <header
      className="flex items-center gap-5 px-5 bg-card border-b border-border"
      style={{ gridArea: 'topbar' }}
    >
      <div className="text-[14px] font-semibold text-foreground">{breadcrumb.title}</div>
      {breadcrumb.sub && (
        <div className="text-[12px] text-muted-foreground">
          <span className="mr-2">/</span>
          <b className="text-foreground font-medium">{breadcrumb.sub}</b>
        </div>
      )}
      {breadcrumb.tail && (
        <div className="text-[12px] text-muted-foreground">
          <span className="mr-2">·</span>
          <span>{breadcrumb.tail}</span>
        </div>
      )}

      <div className="flex-1" />

      <EngineHealthBadge />

      {lastUpdated && (
        <div className="flex items-center gap-2 text-[12px] text-muted-foreground">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 pulse-dot" />
          <span>Live · {relativeTime(lastUpdated)}</span>
        </div>
      )}

      {user && (
        <div ref={menuRef} className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((o) => !o)}
            className="flex items-center gap-2 px-2.5 py-1 rounded border border-border bg-secondary text-foreground text-[12px] cursor-pointer hover:bg-muted transition-colors"
          >
            <span className="w-[22px] h-[22px] rounded-full flex items-center justify-center text-[11px] font-semibold text-primary-foreground bg-primary">
              {user.username.charAt(0).toUpperCase()}
            </span>
            <span>{user.username}</span>
            <span className="text-muted-foreground">·</span>
            <span className="text-muted-foreground">{user.role}</span>
            <span className="ml-1 text-muted-foreground">▾</span>
          </button>

          {menuOpen && (
            <div className="absolute right-0 mt-1 rounded shadow-lg py-1 min-w-[160px] z-10 bg-card border border-border">
              <button
                type="button"
                onClick={handleLogout}
                className="w-full text-left px-3 py-2 text-[12px] text-foreground hover:bg-muted transition-colors"
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      )}
    </header>
  );
}

/**
 * Compute breadcrumb from the URL. Returns {title, sub, tail} — three
 * levels: top-level section / sub-section / contextual tail.
 *
 * <ul>
 *   <li>/config?view=editor   → Configuration / Channel · Editor</li>
 *   <li>/config?view=history  → Configuration / Channel · History</li>
 * </ul>
 */
function routeLabel(pathname: string, search: string): {
  title: string;
  sub?: string;
  tail?: string;
} {
  if (pathname === '/' || pathname === '') return { title: 'Dashboard' };
  if (pathname.startsWith('/change-password'))
    return { title: 'Account', sub: 'Change password' };
  if (pathname.startsWith('/monitoring')) return { title: 'Monitoring' };
  if (pathname.startsWith('/channels')) return { title: 'Channels' };
  if (pathname.startsWith('/config')) {
    const params = new URLSearchParams(search);
    const view = params.get('view');
    const tail = view === 'history' ? 'History' : 'Editor';
    return { title: 'Configuration', sub: 'Channel', tail };
  }
  if (pathname.startsWith('/logs')) return { title: 'Observability', sub: 'Logs' };
  return { title: 'SE-Console' };
}

function relativeTime(d: Date): string {
  const diff = Math.round((Date.now() - d.getTime()) / 1000);
  if (diff < 2) return 'just now';
  if (diff < 60) return `${diff}s ago`;
  const mins = Math.floor(diff / 60);
  return `${mins}m ago`;
}
