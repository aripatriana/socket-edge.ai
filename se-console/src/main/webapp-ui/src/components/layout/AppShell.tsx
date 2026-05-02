import type { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

/**
 * Application shell. 220px sidebar + 52px topbar + scrollable main area.
 * Used by all authenticated pages. Uses inline grid-template-areas for
 * the 2x2 layout (Tailwind doesn't have a clean utility for grid-areas).
 */
export function AppShell({
  children,
  lastUpdated,
}: {
  children: ReactNode;
  lastUpdated?: Date | null;
}) {
  return (
    <div
      className="h-screen overflow-hidden bg-background"
      style={{
        display: 'grid',
        gridTemplateColumns: '220px 1fr',
        gridTemplateRows: '52px 1fr',
        gridTemplateAreas: '"sidebar topbar" "sidebar main"',
      }}
    >
      <Sidebar />
      <Topbar lastUpdated={lastUpdated} />
      <main
        className="overflow-y-auto p-5 bg-background"
        style={{ gridArea: 'main' }}
      >
        {children}
      </main>
    </div>
  );
}
