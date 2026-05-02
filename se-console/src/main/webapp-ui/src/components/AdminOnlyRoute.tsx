import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';

/**
 * Role-gated wrapper. Use inside a {@link ProtectedRoute} — this
 * component assumes authentication has already been checked and the
 * auth store holds a user.
 *
 * <p>Non-admins landing on an admin-only URL (typed or via stale
 * bookmark) are redirected to the dashboard rather than shown a
 * "forbidden" page — the page itself isn't meant to exist for them.
 *
 * <p>Kept as a separate component from {@code ProtectedRoute} so the
 * existing auth flow is not disturbed; the two are composed at the
 * route level:
 *
 * <pre>
 *   &lt;ProtectedRoute&gt;
 *     &lt;AdminOnlyRoute&gt;
 *       &lt;AuditPage /&gt;
 *     &lt;/AdminOnlyRoute&gt;
 *   &lt;/ProtectedRoute&gt;
 * </pre>
 */
export function AdminOnlyRoute({ children }: { children: ReactNode }) {
  const user = useAuthStore((s) => s.user);
  if (!user || user.role !== 'admin') {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
