import { authApi } from '../api/auth';
import { useAuthStore } from '../stores/authStore';

export function DashboardPlaceholder() {
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);

  async function handleLogout() {
    try {
      await authApi.logout();
    } catch {
      // Swallow — we clear client state regardless so the user ends up signed out.
    }
    clear();
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200 px-6 py-3 flex items-center justify-between">
        <h1 className="text-base font-semibold text-slate-900">SE-Console</h1>
        <div className="flex items-center gap-4 text-sm">
          <span className="text-slate-600">
            {user?.username} <span className="text-slate-400">· {user?.role}</span>
          </span>
          <button
            onClick={handleLogout}
            className="text-slate-600 hover:text-slate-900 text-sm"
          >
            Sign out
          </button>
        </div>
      </header>

      <main className="p-8">
        <div className="max-w-3xl mx-auto bg-white border border-slate-200 rounded-lg p-8">
          <h2 className="text-lg font-semibold text-slate-900">Dashboard</h2>
          <p className="text-sm text-slate-600 mt-2">
            Auth module wired up. Chat 3 will replace this with the real dashboard
            (health strip, KPIs, top channels, recent activity).
          </p>
        </div>
      </main>
    </div>
  );
}
