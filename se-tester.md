# CLAUDE.md — SE Tester Project Context

> File ini berisi konteks lengkap project SE Tester untuk Claude Code.
> Baca seluruh file ini sebelum membuat atau mengubah kode apapun.

---

## Identitas Project

```
Nama    : SE Tester (SocketEdge Tester)
Package : id.jalin.setester
Org     : PT Jalin Pembayaran Nusantara — Digital Switching Solution
Tujuan  : ISO 8583 Test Framework untuk Socket Edge v3.0.0
Version : 1.0.0
Build   : Maven (fat JAR via spring-boot-maven-plugin)
```

---

## Apa Itu Socket Edge

Socket Edge adalah ISO 8583 TCP Load Balancer berbasis Netty milik Jalin.
- Terima koneksi dari **acquirer** (client side)
- Teruskan ke **issuer/backend** (server side)
- Correlation: match request↔response via **DE11 (STAN) + DE37 (RRN)**
- Deploy: **2 instance** — master (ACTIVE) + slave (STANDBY)
- CLI monitoring via `jsocket.sh info/metric/queue --all`
- Frame protocol: **4-byte big-endian length prefix** + ISO 8583 body

---

## Tech Stack — Jangan Ganti Tanpa Alasan Kuat

```xml
Java            : 17
Spring Boot     : 3.2.5 (parent POM)
Netty           : 4.1.108.Final
JSch (SSH)      : com.github.mwiede:jsch:0.2.17
GraalVM JS      : org.graalvm.polyglot:polyglot:24.1.2
                  org.graalvm.polyglot:js-community:24.1.2 (type=pom)
Jackson YAML    : jackson-dataformat-yaml (via Spring Boot BOM)
CLI             : info.picocli:picocli:4.7.5
FreeMarker      : 2.3.32
Frontend        : React 18 + Vite (embedded ke JAR via frontend-maven-plugin)
WebSocket       : Spring WebSocket (STOMP)
```

> ⚠️ GraalVM: JANGAN pakai koordinat lama `org.graalvm.sdk:graal-sdk`
> atau `org.graalvm.js:js`. Koordinat yang benar adalah `org.graalvm.polyglot:*`.

---

## Struktur Package — Wajib Diikuti

```
id.jalin.setester/
├── SETesterApplication.java     # Spring Boot main + CLI/Web dual mode
│
├── core/
│   ├── iso/
│   │   ├── IsoMessage.java          # Build & parse ISO 8583
│   │   ├── IsoFieldDefs.java        # DE1–DE128 (FIXED/LLVAR/LLLVAR)
│   │   ├── IsoFramer.java           # Netty decoder+encoder
│   │   └── TemplateFunctions.java   # {{stan()}}, {{rrn()}}, {{pan()}}, dll
│   ├── client/
│   │   ├── IsoClient.java           # TCP client, send() + sendAsync()
│   │   └── CorrelationStore.java    # DE11+DE37 correlation
│   └── server/
│       ├── IsoServer.java           # Mock issuer TCP server
│       └── AutoResponder.java       # Rule-based response generator
│
├── scenario/
│   ├── model/
│   │   ├── Scenario.java            # Root YAML model
│   │   ├── TestStep.java            # SEND/WAIT/LOG/PAUSE
│   │   └── Assertion.java           # 15+ assert types + Severity enum
│   ├── YamlScenarioLoader.java      # Load single/suite/folder
│   ├── ScenarioRunner.java          # Execute + evaluate assertions
│   └── engine/
│       └── JsScriptEngine.java      # GraalJS .js scenario runner
│
├── report/
│   ├── TestResult.java              # Model result (StepResult, AssertionResult)
│   ├── HtmlReportGenerator.java     # Self-contained HTML report
│   └── RunHistoryStore.java         # Persist history.json, detect regression
│
├── se/
│   ├── model/
│   │   ├── SeProperties.java        # Config master+slave dari YAML
│   │   └── SeMetrics.java           # SocketInfo, SocketMetric, SocketQueue
│   ├── SshManager.java              # SSH user+password: execute/read/write/tail
│   ├── SeCommandExecutor.java       # Wrapper jsocket.sh commands
│   └── collector/
│       └── SeMetricsCollector.java  # @Scheduled poll, history 300 points
│
├── api/
│   ├── SeManagerController.java     # REST: SE management via SSH
│   ├── RunnerController.java        # REST: scenario CRUD + run
│   ├── HistoryController.java       # REST: history/trend
│   ├── BadgeController.java         # SVG CI badges
│   ├── MetricsBroadcaster.java      # WebSocket push metrics
│   └── WsProgressHandler.java       # WebSocket live run progress
│
├── config/
│   ├── AppConfig.java               # CORS, static, SPA fallback
│   └── WebSocketConfig.java         # STOMP setup
│
└── cli/
    ├── Main.java                    # Commands: run/suite/validate/server
    └── ClientCommands.java          # Commands: send/signon/load/stress
```

---

## Aturan Kode yang WAJIB Diikuti

### 1. Import — Gunakan yang Benar

```java
// CommandResult ada di SshManager, BUKAN SeCommandExecutor
import id.jalin.setester.se.SshManager.CommandResult;  // ✓
import id.jalin.setester.se.SeCommandExecutor.CommandResult;  // ✗

// Severity ada di Assertion, BUKAN AssertionResult
import id.jalin.setester.scenario.model.Assertion.Severity;  // ✓
import id.jalin.setester.report.TestResult.AssertionResult.Severity;  // ✗
```

### 2. Lambda — Capture Final Variable

```java
// ✗ ERROR: local variable tidak effectively final
int start = 1;
if (condition) start = 2;
computeIfAbsent(key, k -> new AtomicInteger(start)); // compile error

// ✓ BENAR: copy ke final sebelum lambda
final int finalStart = start;
computeIfAbsent(key, k -> new AtomicInteger(finalStart));
```

### 3. IsoClient — Gunakan sendAsync() untuk Concurrent Load

```java
// Single transaction (blocking)
IsoMessage response = client.send(request);

// Concurrent load test (non-blocking)
client.sendAsync(request, timeout)
    .thenAccept(response -> { /* handle */ })
    .exceptionally(ex -> { /* handle error */ return null; });
```

### 4. GraalVM Context — Engine Option yang Benar

```java
// ✓ GraalVM 24.x
Context ctx = Context.newBuilder("js")
    .allowAllAccess(true)
    .option("engine.WarnInterpreterOnly", "false")
    .build();

// ✗ Deprecated di 24.x
Context.newBuilder("js")
    .option("js.ecmascript-version", "2022");
```

### 5. SE Enabled Guard — Semua @Scheduled Harus Cek

```java
@Value("${setester.se.enabled:false}")
private boolean seEnabled;

@Scheduled(fixedDelayString = "${setester.metrics.poll-interval-ms:2000}")
public void collectMetrics() {
    if (!seEnabled || seProperties.getInstances() == null) return;
    // ... polling logic
}
```

### 6. IsoFramer — Deteksi Non-ISO Traffic

Saat first byte > 0x10 (bukan ISO 8583 header), tutup koneksi:
```java
int firstByte = in.getUnsignedByte(in.readerIndex());
if (firstByte > 0x10) {
    ctx.close();
    return null;
}
```

---

## ISO 8583 Frame Format

```
[4 bytes big-endian length][body]
 └── length = body length only (tidak termasuk 4 byte ini)

Body:
[4 bytes MTI ASCII][8 bytes primary bitmap][8 bytes secondary bitmap (optional)][fields...]
```

Contoh: MTI=`0200`, bitmap=`7230000000000000` → field DE2,DE3,DE4,DE7,DE11,DE12,DE13 present.

---

## Correlation Key

```
key = DE11 (STAN, 6 digit) + ":" + DE37 (RRN, 12 digit)
Contoh: "000042:000000000042"
```

Request dan response di-match via key ini di `CorrelationStore`.

---

## Template Functions — Format yang Benar

| Function | Output | Panjang |
|----------|--------|---------|
| `{{stan()}}` | `000042` | 6 digit, sequential, AtomicInteger |
| `{{rrn()}}` | `261102134401` | **12 digit**: yy(2)+doy(3)+hh(2)+mm(2)+sec(2)+seq(1) |
| `{{datetime()}}` | `0412143501` | 10 char: MMddHHmmss |
| `{{time()}}` | `143501` | 6 char: HHmmss |
| `{{date()}}` | `0412` | 4 char: MMdd |
| `{{pan(prefix=4111)}}` | 16 digit Luhn-valid | |
| `{{amount(min=1000,max=99999)}}` | `000000057432` | 12 digit |
| `{{env.SE_HOST}}` | dari env file | |
| `{{vars.TERMINAL_ID}}` | dari scenario variables | |
| `{{steps.STEP_ID.request.DE11}}` | field dari request step sebelumnya | |
| `{{steps.STEP_ID.response.DE39}}` | field dari response step sebelumnya | |

> ⚠️ RRN harus tepat 12 digit. Formula: `yy+doy+hh+mm+sec+seq`
> `2+3+2+2+2+1 = 12`. Jangan sampai kurang atau lebih.

---

## Assertion Severity

```java
public enum Severity {
    HARD,  // fail → stop step, skip remaining steps
    SOFT,  // fail → warn di report, continue
    INFO   // always pass, hanya dicatat
}
```

`Severity` ada di `id.jalin.setester.scenario.model.Assertion.Severity`.

---

## application.yml — Key Properties

```yaml
setester:
  se:
    enabled: ${SE_ENABLED:false}    # FALSE by default — app up tanpa SE
    instances:
      - id: master
        host: ${SE_MASTER_HOST:192.168.1.10}
        role: MASTER
        ssh:
          username: ${SE_MASTER_SSH_USER:jalin}
          password: ${SE_MASTER_SSH_PASS:changeme}
          connect-timeout-ms: 5000  # jangan terlalu besar, bisa block startup
        paths:
          install-dir: /opt/jalin-isoloadbalancer
          channel-conf: /opt/jalin-isoloadbalancer/conf/channel.conf
      - id: slave
        role: SLAVE
        # ... sama dengan master
  metrics:
    poll-interval-ms: 2000          # polling SE metrics
    info-poll-interval-ms: 10000    # polling SE info (status/conn)
  storage:
    scenarios-dir: ./scenarios
    reports-dir: ./reports
```

---

## Dual Mode — CLI vs Web

```java
// SETesterApplication.java
public static void main(String[] args) {
    if (args.length > 0 && isCliCommand(args[0])) {
        Main.main(args);  // CLI mode — langsung exit setelah selesai
        return;
    }
    SpringApplication.run(SETesterApplication.class, args);  // Web mode
}

private static boolean isCliCommand(String arg) {
    return switch (arg) {
        case "run", "suite", "validate", "server",
             "send", "signon", "load", "stress" -> true;
        default -> false;
    };
}
```

---

## CLI Commands — Semua Yang Ada

```bash
# Test Framework (dengan assertion + HTML report)
java -jar setester.jar run      <scenario.yaml> [--env env.yaml] [-o report.html]
java -jar setester.jar suite    <suite.yaml>    [--env env.yaml] [-o report.html]
java -jar setester.jar validate <folder|file>

# Mock Server (simulasi issuer/backend)
java -jar setester.jar server   --port 9100 [--delay 100]

# Client Mode (load generator tanpa assertion)
java -jar setester.jar send     -h HOST -p PORT --mti 0200 [--pan X] [--amount X] [-v]
java -jar setester.jar signon   -h HOST -p PORT [--nmic 001|002|301|201]
java -jar setester.jar load     -h HOST -p PORT --tps 100 --duration 60 [--connections 5] [-o out.csv]
java -jar setester.jar stress   -h HOST -p PORT --max-tps 500 [--start-tps 10] [--duration 120]

# Web UI
java -jar setester.jar          # → http://localhost:8080
```

---

## REST API — Semua Endpoint

```
# Scenario Management
GET    /api/scenarios              list all YAML files
GET    /api/scenarios/**?path=X    get content
PUT    /api/scenarios              save {path, content}
DELETE /api/scenarios?path=X       delete file
POST   /api/scenarios/validate     validate YAML {content}

# Test Runner
POST   /api/run/scenario           {scenarioPath|content, envPath, envVars} → {runId}
POST   /api/run/suite              {suitePath, envPath} → {runId}
GET    /api/run/status/{runId}     {runId, status, reportPath}
GET    /api/reports                list HTML reports
GET    /api/reports/{name}         HTML content

# SE Manager
GET    /api/se/instances           list instances
GET    /api/se/status              all latest metrics
GET    /api/se/metrics/{id}        latest metrics for instance
GET    /api/se/metrics/{id}/history  300 data points
GET    /api/se/{id}/config         baca channel.conf
PUT    /api/se/{id}/config         {content} → save ke SE via SSH
POST   /api/se/{id}/config/validate  jsocket.sh validate
POST   /api/se/{id}/config/reload    jsocket.sh reload
POST   /api/se/config/validate-reload-all  semua instance
POST   /api/se/config/sync-to-slave  copy master conf ke slave
GET    /api/se/{id}/log?lines=100  tail log via SSH
POST   /api/se/{id}/start-all
POST   /api/se/{id}/stop-all
POST   /api/se/{id}/restart-all
POST   /api/se/{id}/channel/{name}/start
POST   /api/se/{id}/channel/{name}/stop
POST   /api/se/{id}/channel/{name}/restart

# History & Trend
GET    /api/history?limit=20&suite=X  run history
GET    /api/history/latest            latest run
GET    /api/history/compare?suite=X   regression comparison
GET    /api/history/stats             aggregate stats

# CI/CD Badges (SVG)
GET    /api/badge/status.svg
GET    /api/badge/latency.svg
GET    /api/badge/regression.svg

# WebSocket (STOMP)
/ws                              handshake endpoint
/topic/metrics/{instanceId}      live SE metrics (2s)
/topic/metrics/summary           combined all instances
/topic/run/{runId}               live run progress events
```

---

## WebSocket Event Types (run progress)

```json
{ "type": "START",         "runId": "run_123", "scenario": "..." }
{ "type": "SCENARIO_START","index": 1, "scenario": "..." }
{ "type": "SCENARIO_DONE", "index": 1, "scenario": "...", "status": "PASSED", "passed": 3, "total": 3, "duration": 245 }
{ "type": "COMPLETE",      "runId": "run_123", "passed": 5, "total": 5, "reportPath": "20260412_report.html" }
{ "type": "ERROR",         "runId": "run_123", "error": "Connection refused" }
```

---

## Frontend Pages

| Page | Route | Fungsi |
|------|-------|--------|
| Dashboard | `/` | SE cluster cards, live metrics, quick actions, recent reports |
| Scenarios | `/scenarios` | List YAML files grouped by folder, run/edit/delete |
| ScenarioEditor | `/scenarios/edit` | YAML textarea + validate + save & run |
| TestRunner | `/runner` | Select scenario/suite, live WebSocket progress log |
| SeManager | `/se` | Per-instance tabs, socket table, latency chart, log viewer |
| ConfigEditor | `/config` | Step-by-step fetch→edit→validate→reload, auto-sync slave |
| ReportList | `/reports` | Preview iframe, open in tab |
| HistoryPage | `/history` | Trend charts, regression alert, run table, CI badges |

---

## Scenario YAML — Full Template

```yaml
name: "SCENARIO_NAME"
description: "Deskripsi singkat"
tags: [regression, p1, financial]    # tag bebas untuk filter
timeout: 10000                        # ms per step, default 30000

setup:
  server:                             # optional: start mock issuer internal
    port: 9100
    autoRespond: true
    delayMs: 0
  connect:                            # connect ke Socket Edge sebagai client
    host: "{{env.SE_HOST}}"
    port: "{{env.SE_PORT}}"
    timeout: 10000

variables:                            # diakses via {{vars.KEY}}
  TERMINAL_ID: "TERM0001"
  MERCHANT_ID: "MERCH000000001"

steps:
  - id: step_unique_id                # wajib unik dalam scenario
    name: "Step Description"
    action: SEND                      # SEND | WAIT | LOG | PAUSE
    skipOnFail: false                 # default false = stop on fail

    # Untuk action SEND:
    message:
      mti: "0200"
      fields:
        DE2:  "{{pan(prefix=4111)}}"
        DE3:  "000000"
        DE4:  "{{amount(min=10000,max=999999)}}"
        DE7:  "{{datetime()}}"
        DE11: "{{stan()}}"
        DE12: "{{time()}}"
        DE13: "{{date()}}"
        DE22: "051"
        DE25: "00"
        DE37: "{{rrn()}}"
        DE41: "{{vars.TERMINAL_ID}}"
        DE42: "{{vars.MERCHANT_ID}}"
        DE49: "360"

    # Latency SLA di step level
    latencySla:
      warn: 300     # ms — soft assertion
      fail: 1000    # ms — hard assertion

    assertions:
      # MTI check
      - mti: "0210"
        severity: HARD

      # Field exact match
      - field: DE39
        equals: "00"
        severity: HARD
        message: "Custom failure message"

      # Whitelist
      - field: DE39
        in: ["00", "01"]

      # Exclusion
      - field: DE39
        notEquals: "91"
        severity: SOFT

      # Not empty
      - field: DE38
        notEmpty: true
        severity: SOFT

      # Field exists in bitmap
      - field: DE55
        exists: true

      # Regex
      - field: DE38
        matches: "[A-Z0-9]{6}"

      # Cross-step echo-back check
      - field: DE11
        equalsField: "{{steps.step_unique_id.request.DE11}}"
        severity: HARD

      # Numeric range
      - field: DE4
        greaterThan: "000000000000"

      - field: DE4
        between:
          min: "000000010000"
          max: "000009999999"

      # Latency assertion inline
      - latency:
          warn: 300
          fail: 1000

      # JS expression
      - expression: "response.DE39 === '00'"
        severity: SOFT
        message: "RC should be approved"

      # Conditional assertion
      - field: DE38
        notEmpty: true
        severity: SOFT
        onlyIf: "{{steps.step_unique_id.response.DE39}} == 00"

    # Untuk action WAIT:
    # waitMs: 1000

    # Untuk action LOG:
    # logMessage: "Pesan log: {{stan()}}"

    # Untuk action PAUSE:
    # pauseMs: 2000

# Performance SLA di scenario level
sla:
  avgLatencyMs: 500
  p95LatencyMs: 2000
  maxLatencyMs: 5000
  minSuccessRate: 99.0

teardown:
  disconnect: true    # putus koneksi client
  stopServer: true    # stop mock server
```

---

## JS Scenario — Context yang Tersedia

```javascript
// Semua ini tersedia di script .js:

// Client
await client.connect({ host: 'x.x.x.x', port: 9999, timeout: 10000 })
const res = await client.send({ mti: '0200', DE2: '...', DE11: stan() })
client.disconnect()

// Server
await server.start({ port: 9100, autoRespond: true, delayMs: 0 })
server.stop()
const count = server.getReceivedCount()

// Assertions
assert.equals(actual, expected, 'message')
assert.notEquals(actual, unexpected, 'message')
assert.in(actual, ['00', '01'], 'message')
assert.notEmpty(actual, 'message')
assert.lessThan(latency, 2000, 'message')
assert.warn(actual, expected, 'message')  // soft assert
assert.matches(actual, '[A-Z]{6}', 'message')

// Template functions
stan()                     // "000042"
rrn()                      // "261102134401"
pan('prefix=4111')         // "4111111111111118"
pan('')                    // random Luhn-valid
amount('min=1000,max=9999') // "000000005432"
amount('10000')            // "000000010000"
datetime()                 // "0412143501"
time()                     // "143501"
date()                     // "0412"

// Utilities
sleep(2000)               // pause 2 detik
log.info('message')       // log ke report
log.warn('message')
log.error('message')

// Environment
env.SE_HOST               // dari application.yml / env vars
env.SE_PORT
```

---

## SshManager — Method yang Tersedia

```java
// Execute command, return stdout + stderr + exitCode
CommandResult result = sshManager.execute(instanceId, "jsocket.sh info --all");

// Read remote file
String content = sshManager.readFile(instanceId, "/path/to/channel.conf");

// Write remote file (dengan backup otomatis)
sshManager.writeFile(instanceId, "/path/to/channel.conf", content);

// Tail log (streaming)
sshManager.tailLog(instanceId, "/path/to/log", 100, line -> {
    System.out.println(line);
});

// CommandResult
result.isSuccess()    // exitCode == 0
result.getStdout()
result.getStderr()
result.getExitCode()
result.getCommand()
```

---

## SeCommandExecutor — jsocket.sh Wrappers

```java
// Lifecycle
executor.startAll(instanceId)
executor.stopAll(instanceId)
executor.restartAll(instanceId)
executor.startChannel(instanceId, "finnet")
executor.stopChannel(instanceId, "finnet")
executor.restartChannel(instanceId, "finnet")

// Config
executor.validate(instanceId)          // jsocket.sh validate
executor.reload(instanceId)            // jsocket.sh reload
executor.readChannelConf(instanceId)   // cat channel.conf
executor.writeChannelConf(instanceId, content)  // write + backup
executor.syncConfigToSlave()           // copy master → slave
executor.validateAndReloadAll()        // validate + reload semua instance
executor.listBackups(instanceId)
executor.restoreBackup(instanceId, backupPath)

// Monitoring
executor.infoAll(instanceId)           // jsocket.sh info --all
executor.metricAll(instanceId)         // jsocket.sh metric --all
executor.queueAll(instanceId)          // jsocket.sh queue --all
executor.getLog(instanceId, 100)       // tail -n 100 log

// SyncResult
result.allValidatePassed()
result.allReloadPassed()
result.getValidates()   // Map<String, SshManager.CommandResult>
result.getReloads()     // Map<String, SshManager.CommandResult>
```

---

## HTML Report — Sections

1. **Header** — logo, tanggal, durasi
2. **Status Banner** — PASSED/FAILED besar
3. **Summary Cards** — Total/Passed/Failed/Success Rate
4. **Performance Summary** — AVG/P90/P95/MAX latency
5. **SE Metrics** — tabel ALAT/L95/TTPS + sparkline (jika SE enabled)
6. **Comparison** — delta vs previous run (jika ada history)
7. **Latency Distribution** — histogram 7 bucket
8. **Test Results** — per scenario, expandable, drill-down per step
   - Request fields (PAN masked)
   - Response fields (RC colored)
   - Assertion results table

---

## RunHistoryStore — Data yang Disimpan

```json
// reports/history.json — array of RunSummary
[
  {
    "id": "a1b2c3d4",
    "timestamp": "2026-04-12T08:30:00",
    "suiteName": "Full Regression Suite",
    "reportFile": "20260412_083000_full_regression.html",
    "total": 15,
    "passed": 15,
    "failed": 0,
    "errors": 0,
    "successRate": 100.0,
    "totalDurationMs": 720000,
    "avgLatencyMs": 487,
    "p95LatencyMs": 1200,
    "maxLatencyMs": 3400
  }
]
```

Regression detection: `current.failed > previous.failed || current.avgLatencyMs > previous.avgLatencyMs * 1.2`

---

## Known Issues — Jangan Diulangi

| Issue | Penyebab | Fix |
|-------|----------|-----|
| `org.graalvm.js:js` not found | Koordinat lama | Pakai `org.graalvm.polyglot:js-community:24.1.2` |
| `SeCommandExecutor.CommandResult` not found | `CommandResult` ada di `SshManager` | Import `SshManager.CommandResult` |
| `AssertionResult.Severity` not found | `Severity` di `Assertion`, bukan `AssertionResult` | Import `Assertion.Severity` |
| Lambda non-final variable | `start`/`pad` di-assign di try block | Copy ke `final int finalX = x` sebelum lambda |
| RRN hanya 10 digit | Formula salah | `yy(2)+doy(3)+hh(2)+mm(2)+sec(2)+seq(1)=12` |
| App tidak up tanpa SE | SSH attempt saat startup | `SE_ENABLED=false` default, guard semua `@Scheduled` |
| HTTP request ke mock server | Browser connect ke port ISO server | Deteksi `firstByte > 0x10`, close connection |

---

## Build & Run

```bash
# Prerequisites: Java 17+, Maven 3.8+, Node 20+ (untuk frontend)

# Build semua (Java + React)
./build.sh
# atau
mvn clean package -DskipTests

# Build skip tests (lebih cepat)
./build.sh --skip-tests

# Run web (tanpa SE, default)
java -jar target/setester-1.0.0.jar

# Run web (dengan SE)
SE_ENABLED=true \
SE_MASTER_HOST=192.168.1.10 \
SE_MASTER_SSH_USER=jalin \
SE_MASTER_SSH_PASS=secret \
SE_SLAVE_HOST=192.168.1.11 \
SE_SLAVE_SSH_PASS=secret \
java -jar target/setester-1.0.0.jar

# Test framework CLI
java -jar target/setester-1.0.0.jar validate scenarios/
java -jar target/setester-1.0.0.jar run scenarios/financial/fin01_happy_path.yaml
java -jar target/setester-1.0.0.jar suite suites/smoke_test.yaml -o report.html

# Client mode CLI
java -jar target/setester-1.0.0.jar send   -h 192.168.1.10 -p 9999 --mti 0200 -v
java -jar target/setester-1.0.0.jar signon -h 192.168.1.10 -p 9999
java -jar target/setester-1.0.0.jar load   -h 192.168.1.10 -p 9999 --tps 100 --duration 60
java -jar target/setester-1.0.0.jar stress -h 192.168.1.10 -p 9999 --max-tps 500
```

---

## Test Suites yang Ada

```
suites/smoke_test.yaml       6 scenario P1, ~2-3 menit, stopOnFail=true
suites/regression_full.yaml  15 scenario, ~10-15 menit
suites/nightly.yaml          17 scenario + failover, ~20-25 menit
```

Scenario tersedia di:
```
scenarios/financial/   fin01-04 (happy path, tx types, amount, PAN)
scenarios/decline/     dec01-02 (response codes, system codes)
scenarios/reversal/    rev01-02 (full reversal, advice 0421)
scenarios/network/     net01-02 (lifecycle, repeat echo)
scenarios/regression/  reg01-04 (correlation, passthrough, MTI, multi-session)
scenarios/load/        load01 (YAML baseline), load_js_01 (JS dynamic)
scenarios/failover/    fail01-02 (YAML), fail_js_01 (JS complex)
```

---

## Prinsip Pengembangan

1. **Core engine tidak boleh diubah sembarangan** — `IsoMessage`, `IsoFramer`, `CorrelationStore` adalah fondasi; perubahan kecil bisa break semua scenario
2. **SE tidak boleh down karena tester** — semua SSH call harus ada timeout dan catch exception; jangan block startup
3. **Report harus self-contained** — satu file `.html` bisa dibuka offline, tidak boleh depend ke CDN
4. **Scenario YAML harus backward compatible** — kalau ada field baru di model, beri default value
5. **Test dulu sebelum tambah fitur** — jalankan `mvn test` sebelum commit; unit test di `src/test/java`

---

*Dokumen ini wajib diupdate setiap ada perubahan arsitektur signifikan.*
*Last updated: Juni 2026*
