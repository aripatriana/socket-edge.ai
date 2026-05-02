import { Outlet } from 'react-router-dom';
import { AppShell } from '../../components/layout/AppShell';
import { SubTabs, type SubTab } from '../../components/layout/SubTabs';

/**
 * Monitoring section layout. Sub-tabs per Foundation 6.1.
 * System + JVM + JVM-SE + Network are the live tabs.
 *
 * ISOLB tab removed — no longer part of the monitoring surface. Legacy
 * references in design-mockup HTMLs (monitoring-isolb.html) should be
 * treated as obsolete.
 */
const MONITORING_TABS: SubTab[] = [
  { to: '/monitoring/system', label: 'System', icon: '◎' },
  { to: '/monitoring/jvm', label: 'JVM', icon: '☕' },
  { to: '/monitoring/network', label: 'Network', icon: '≡' },
];

export function MonitoringLayout() {
  return (
    <AppShell>
      <SubTabs tabs={MONITORING_TABS} />
      <Outlet />
    </AppShell>
  );
}
