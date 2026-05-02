# Chat 3a — System Metrics Dashboard (Tailwind + shadcn tokens edition)

Live CPU / Memory / Disk / File Descriptor dashboard. All components use
Tailwind utility classes mapped to the shadcn color tokens already in your
`index.css` (`bg-card`, `text-foreground`, `text-primary`, etc.) — no new
CSS variables are introduced.

Primary color stays as defined in Chat 1: Jalin red.

## Files in this bundle

```
src/main/java/id/co/jalin/seconsole/
├── dto/response/SystemMetricsDto.java      NEW
├── service/SystemMetricsService.java       NEW — OSHI probe, 2s cache
└── controller/MetricsController.java       NEW — GET /api/system/metrics

webapp-ui/src/
├── main.tsx                 REPLACES Chat 2 — adds QueryClientProvider
├── App.tsx                  REPLACES Chat 2 — Dashboard instead of placeholder
├── index.css.additions      APPEND pulse-dot keyframe to end of index.css
├── api/
│   ├── metrics.types.ts     NEW
│   └── metrics.ts           NEW
├── hooks/
│   ├── useSystemMetrics.ts  NEW
│   └── useMetricHistory.ts  NEW
├── lib/
│   └── format.ts            NEW
├── components/
│   ├── layout/
│   │   ├── AppShell.tsx     NEW
│   │   ├── Sidebar.tsx      NEW
│   │   └── Topbar.tsx       NEW
│   └── dashboard/
│       ├── Card.tsx         NEW (thin wrapper, swap-in point for shadcn Card later)
│       ├── Sparkline.tsx    NEW
│       ├── ChartCard.tsx    NEW
│       ├── KpiCard.tsx      NEW
│       ├── HealthStrip.tsx  NEW
│       ├── CpuChart.tsx     NEW
│       ├── MemoryChart.tsx  NEW
│       └── DiskUsage.tsx    NEW
└── routes/
    └── Dashboard.tsx        NEW (replaces DashboardPlaceholder from Chat 2)

_integration/
├── pom-additions.xml
├── frontend-deps.md
└── README.md
```

## Integration steps

### 1. Copy files

Drop-in replacements. Paths match your project layout.

`DashboardPlaceholder.tsx` from Chat 2 is unused now — safe to leave or delete.

### 2. Add OSHI to pom.xml

Merge `_integration/pom-additions.xml` into the existing `<dependencies>` block.
Single new dep: `com.github.oshi:oshi-core:6.6.5`.

### 3. Append to index.css

**Only add the keyframe block** from `webapp-ui/src/index.css.additions` to the
**end** of `src/main/webapp-ui/src/index.css`, after `@tailwind utilities`.

Keep your existing shadcn `@layer base` block (with `--primary`, `--card`, etc.)
exactly as-is. Chat 3a components reference those tokens via Tailwind utilities,
not via `var(--)` — so nothing in your existing CSS needs to change.

### 4. Install frontend dependencies

```
cd src/main/webapp-ui
npm install @tanstack/react-query recharts
```

### 5. Build and run

Dev mode (hot reload):
```
# Terminal 1
java -jar target/se-console-0.1.0-SNAPSHOT.jar

# Terminal 2
cd src/main/webapp-ui
npm run dev
# Open http://localhost:5173
```

Production bundle:
```
mvn clean package
java -jar target/se-console-0.1.0-SNAPSHOT.jar
# Open http://localhost:8080
```

## Expected UI

- **Sidebar**: Dark navy, Jalin-red brand mark (J), nav items with "soon" tags
  for items not yet built (Channels, Monitoring, Logs, etc.)
- **Topbar**: Page title + live indicator (pulsing green dot) + user chip
  with sign-out dropdown
- **Health strip**: CPU %, Memory % + used/total, FD count, Uptime, Load avg,
  each with a mini sparkline
- **KPI grid** (4 cards): CPU, Memory, File Descriptors, Load Avg (1m), each
  with bigger sparkline and secondary stats
- **Charts row** (2 columns): CPU Usage area chart (system in primary red,
  process outlined), Memory Usage area chart (accent blue)
- **Disk Usage card**: Per-mount bars, colored by utilization (accent <75%,
  amber 75–90%, destructive 90%+)
- **Host info footer**: Hostname, OS, Architecture, Uptime

## Tokens in use (from your existing `index.css`)

| Token | Used for |
|---|---|
| `bg-background` | Page background, main area |
| `bg-card` | Every card (sidebar excepted), topbar, dropdown |
| `bg-primary` | Brand mark, user avatar, active sidebar border, CPU chart |
| `bg-accent` | Memory chart, disk bars (low utilization) |
| `bg-muted` | User chip, disk bar track, dropdown hover |
| `bg-destructive` | Error banner, disk bar (>=90%) |
| `text-foreground` | All body text |
| `text-muted-foreground` | Labels, sub-text, stats |
| `text-primary-foreground` | Text on red surfaces (brand mark) |
| `border-border` | All borders |

Sidebar uses hardcoded `slate-900` / `slate-800` since admin dashboards
conventionally keep that surface dark regardless of overall theme.

## Platform behavior notes

- **Windows**: Load average and FD count show "N/A" / "Linux only" — expected
- **First few seconds**: CPU % may show 0.0% (needs 2 tick samples for delta)
- **Sparklines**: Start empty, fill in over ~10 seconds

## Known limits

- History is browser-memory only. Page reload resets charts.
- No WebSocket yet — polling every 2s via HTTP.
- No engine metrics yet — those come in Chat 3b (JVM) / 3c (channels).

## Next chat handoff

- **Chat 3b**: JMX client + Monitoring → JVM tab (heap, GC, threads)
- **Chat 3c**: Engine JMX integration + Channels list + Channel detail
