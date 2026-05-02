import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';
import { ApiError } from '../../api/client';
import { useAuthStore } from '../../stores/authStore';

export function LoginPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!username.trim() || !password) {
      setError('Username and password are required.');
      return;
    }

    setSubmitting(true);
    try {
      const result = await authApi.login(username.trim(), password);
      setAuth({
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        user: result.user,
      });
      navigate(result.mustChangePassword ? '/change-password' : '/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) setError('Invalid username or password.');
        else if (err.code === 'ACCOUNT_DISABLED') setError('This account is disabled.');
        else if (err.code === 'ACCOUNT_LOCKED') setError('Account is locked. Contact an administrator.');
        else setError(err.message);
      } else {
        setError('Network error. Try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm bg-white shadow-sm border border-slate-200 rounded-lg p-8">
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-slate-900">SE-Console</h1>
          <p className="text-sm text-slate-500 mt-1">Sign in to continue</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="username" className="block text-xs font-medium text-slate-700 mb-1">
              Username
            </label>
            <input
              id="username"
              name="username"
              type="text"
              autoComplete="username"
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              disabled={submitting}
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-xs font-medium text-slate-700 mb-1">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              disabled={submitting}
            />
          </div>

          {error && (
            <div role="alert" className="text-sm text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium py-2 rounded transition-colors"
          >
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </div>
  );
}
