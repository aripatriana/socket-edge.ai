import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from './routes/auth/LoginPage';
import { ChangePasswordPage } from './routes/auth/ChangePasswordPage';
import { Dashboard } from './routes/Dashboard';
import { MonitoringLayout } from './routes/monitoring/MonitoringLayout';
import { JvmPage } from './routes/monitoring/JvmPage';
import { SystemPage } from './routes/monitoring/SystemPage';
import { NetworkPage } from './routes/monitoring/NetworkPage';
import { ChannelsPage } from './routes/channels/ChannelsPage';
import { ChannelDetailPage } from './routes/channels/ChannelDetailPage';
import { ConfigPage } from './routes/config/ConfigPage';
import { LogsPage } from './routes/logs/LogsPage';
import { AuditPage } from './routes/audit/AuditPage';
import { UsersPage } from './routes/users/UsersPage';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AdminOnlyRoute } from './components/AdminOnlyRoute';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/change-password"
        element={
          <ProtectedRoute>
            <ChangePasswordPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/channels"
        element={
          <ProtectedRoute>
            <ChannelsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/channels/:name"
        element={
          <ProtectedRoute>
            <ChannelDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/channels/:name/:tab"
        element={
          <ProtectedRoute>
            <ChannelDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/config"
        element={
          <ProtectedRoute>
            <ConfigPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/config/:fileName"
        element={
          <ProtectedRoute>
            <ConfigPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/config/history"
        element={<Navigate to="/config" replace />}
      />

      <Route
        path="/logs"
        element={
          <ProtectedRoute>
            <LogsPage />
          </ProtectedRoute>
        }
      />

      {/* Admin: Audit Trail. */}
      <Route
        path="/audit"
        element={
          <ProtectedRoute>
            <AdminOnlyRoute>
              <AuditPage />
            </AdminOnlyRoute>
          </ProtectedRoute>
        }
      />

      {/* Admin: Users & Roles. */}
      <Route
        path="/users"
        element={
          <ProtectedRoute>
            <AdminOnlyRoute>
              <UsersPage />
            </AdminOnlyRoute>
          </ProtectedRoute>
        }
      />

      <Route
        path="/monitoring"
        element={
          <ProtectedRoute>
            <MonitoringLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/monitoring/system" replace />} />
        <Route path="system" element={<SystemPage />} />
        <Route path="jvm" element={<JvmPage />} />
        <Route path="network" element={<NetworkPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
