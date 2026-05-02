import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { UserInfo } from '../api/auth';

/**
 * Auth state. Persisted to sessionStorage so:
 *   - Page reload keeps the user signed in.
 *   - Closing the tab clears the session (natural "end of work" signal).
 *   - Token expiry (8h) triggers auto-logout via api client's 401 handler.
 *   - Manual Sign out clears state and navigates to /login.
 *
 * sessionStorage chosen over localStorage because an ops console is often
 * used on shared machines — we don't want a forgotten session to survive
 * a browser restart.
 */
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserInfo | null;
  isAuthenticated: boolean;

  setAuth: (args: { accessToken: string; refreshToken: string; user: UserInfo }) => void;
  updateUser: (user: UserInfo) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,

      setAuth: ({ accessToken, refreshToken, user }) =>
        set({ accessToken, refreshToken, user, isAuthenticated: true }),

      updateUser: (user) => set({ user }),

      clear: () =>
        set({ accessToken: null, refreshToken: null, user: null, isAuthenticated: false }),
    }),
    {
      name: 'se-console-auth',
      storage: createJSONStorage(() => sessionStorage),
      // Only persist these fields; methods shouldn't be stored.
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
