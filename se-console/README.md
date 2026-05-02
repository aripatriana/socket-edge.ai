# SE-Console

**Socket Edge Console** — web-based operations console for the Jalin ISO Load Balancer engine.

> Phase 1: Foundation bootstrap. Boots, serves a landing page, probes the
> backend via `/api/health`. Auth, metrics, config editor, and real dashboards
> arrive in subsequent phases.

---

## Tech stack

| Layer    | Choice                                                |
| -------- | ----------------------------------------------------- |
| Runtime  | Java 21 LTS                                           |
| Backend  | Spring Boot 3.4.x (Web, Security, Data JPA, WebSocket, Actuator, Validation) |
| DB       | H2 embedded (file mode) + Flyway migrations           |
| Auth     | Spring Security + JWT (jjwt 0.12)                     |
| Metrics  | OSHI (OS), JMX (engine)                               |
| HOCON    | Typesafe Config                                       |
| Frontend | React 18 + TypeScript + Vite + Tailwind + shadcn/ui   |
| Build    | Maven 3.9+ (mvn wrapper)                              |
| Package  | Executable fat JAR (~30–35 MB target, 40 MB hard cap) |

See `docs/` and the Development Foundation Guide for architectural rationale.

---

## Prerequisites

- **JDK 21** (Eclipse Temurin / OpenJDK)
- **Maven 3.9+** *(or just use the wrapper: `./mvnw`)*
- **Node.js 20 LTS + npm** — *only needed if you want to run the UI standalone
  via `npm run dev`. The Maven build installs its own private Node toolchain,
  so the fat-JAR pipeline does not require a system Node.*
- **Git**

---

## Quickstart (3 commands)

Clone, install dependencies, run:

```bash
# 1. One-time build (downloads deps, installs private Node, builds UI, packs JAR)
./mvnw clean package

# 2. Run the fat JAR
java -jar target/se-console-0.1.0-SNAPSHOT.jar

# 3. Open the console
open http://localhost:8080
```

You should see a landing card reading **Backend status: UP** with the service
name, version, and server timestamp. An H2 file `data/seconsole.mv.db`
is created in the working directory.

### Faster dev loop

Running the full Maven package on every UI tweak is slow. Use this split:

```bash
# Terminal A — backend with live reload
./mvnw spring-boot:run

# Terminal B — Vite dev server with API proxy (proxies /api, /actuator, /ws to :8080)
cd src/main/webapp-ui
npm install
npm run dev
```

Open **http://localhost:5173** for the frontend; Spring Boot stays on
**http://localhost:8080** and serves the API.

---

## Default login

| Field    | Value                 |
| -------- | --------------------- |
| Username | `admin`               |
| Password | `changeme`            |

> The account is flagged `must_change_password = TRUE`. Authentication is
> not wired in the bootstrap milestone yet — this credential becomes active
> once Chat 2 (Auth module) lands.

---

## Project layout

```
se-console/
├── pom.xml                          Maven build
├── src/
│   ├── main/
│   │   ├── java/id/co/jalin/seconsole/   Java sources
│   │   ├── resources/
│   │   │   ├── application.yml      Default config
│   │   │   ├── logback-spring.xml   Logging
│   │   │   ├── db/migration/        Flyway SQL
│   │   │   └── static/              (generated — UI build output)
│   │   └── webapp-ui/               React + Vite source
│   └── test/java/id/co/jalin/seconsole/
├── scripts/                         Dev / packaging helpers
├── deployment/                      systemd, start/stop scripts, prod conf
│   ├── bin/
│   ├── conf/
│   └── systemd/
└── docs/                            Architecture, API, deployment notes
```

---

## Configuration overrides (production)

Defaults ship inside the JAR. Operators override via (highest wins):

1. `/opt/jalin-isoloadbalancer/conf/seconsole.yml` — external file
2. Environment variables — `SECONSOLE_SECURITY_JWT_SECRET`, `SECONSOLE_JMX_SERVICE_URL`, …
3. JVM args — `-Dseconsole.isolb.install-dir=…`

See `deployment/conf/seconsole.yml` for a reference template.

---

## License

Internal. © PT Jalin Pembayaran Nusantara.
