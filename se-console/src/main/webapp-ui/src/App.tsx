// Root App component — router setup.
// main.tsx wraps <App /> with QueryClientProvider + BrowserRouter.

import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from './routes/auth/LoginPage';
import { ChangePasswordPage } from './routes/auth/ChangePasswordPage';
import { Dashboard } from './routes/Dashboard';
import { ProtectedRoute } from './components/ProtectedRoute';

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

      {/* Unknown routes: back to dashboard (which will redirect to /login if needed). */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
