import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';
import { ApiError } from '../../api/client';
import { useAuthStore } from '../../stores/authStore';

const POLICY_HINT =
  'Minimum 8 characters, including one uppercase letter, one lowercase letter, and one digit.';

function validateClientSide(pw: string, username: string | undefined): string | null {
  if (pw.length < 8) return 'Password must be at least 8 characters.';
  if (!/[A-Z]/.test(pw)) return 'Password must contain an uppercase letter.';
  if (!/[a-z]/.test(pw)) return 'Password must contain a lowercase letter.';
  if (!/[0-9]/.test(pw)) return 'Password must contain a digit.';
  if (username && pw.toLowerCase() === username.toLowerCase()) return 'Password must not match username.';
  return null;
}

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const updateUser = useAuthStore((s) => s.updateUser);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!user) {
      setError('You must be signed in to change your password.');
      return;
    }
    if (newPassword !== confirm) {
      setError('New password and confirmation do not match.');
      return;
    }
    if (newPassword === currentPassword) {
      setError('New password must differ from current password.');
      return;
    }
    const policyError = validateClientSide(newPassword, user.username);
    if (policyError) {
      setError(policyError);
      return;
    }

    setSubmitting(true);
    try {
      await authApi.changePassword(user.username, currentPassword, newPassword);
      updateUser({ ...user, mustChangePassword: false });
      navigate('/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'SAME_PASSWORD') setError('New password must differ from current password.');
        else if (err.status === 401) setError('Current password is incorrect.');
        else if (err.code === 'VALIDATION_ERROR') setError('Password does not meet policy requirements.');
        else setError(err.message);
      } else {
        setError('Network error. Try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  const mustChange = user?.mustChangePassword === true;

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm bg-white shadow-sm border border-slate-200 rounded-lg p-8">
        <div className="mb-6">
          <h1 className="text-xl font-semibold text-slate-900">Change password</h1>
          <p className="text-sm text-slate-500 mt-1">
            {mustChange
              ? 'You must change your password before continuing.'
              : 'Update your account password.'}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="currentPassword" className="block text-xs font-medium text-slate-700 mb-1">
              Current password
            </label>
            <input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              autoFocus
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              disabled={submitting}
            />
          </div>

          <div>
            <label htmlFor="newPassword" className="block text-xs font-medium text-slate-700 mb-1">
              New password
            </label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              disabled={submitting}
              aria-describedby="policy-hint"
            />
            <p id="policy-hint" className="text-xs text-slate-500 mt-1">
              {POLICY_HINT}
            </p>
          </div>

          <div>
            <label htmlFor="confirm" className="block text-xs font-medium text-slate-700 mb-1">
              Confirm new password
            </label>
            <input
              id="confirm"
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
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
            {submitting ? 'Updating…' : 'Update password'}
          </button>
        </form>
      </div>
    </div>
  );
}
