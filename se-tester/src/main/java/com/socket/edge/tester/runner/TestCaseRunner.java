package com.socket.edge.tester.runner;

import com.socket.edge.tester.core.client.IsoClient;
import com.socket.edge.tester.core.iso.IsoMessage;
import com.socket.edge.tester.core.iso.TemplateFunctions;
import com.socket.edge.tester.core.server.IsoServer;
import com.socket.edge.tester.loader.CsvDataLoader;
import com.socket.edge.tester.loader.YamlKeywordLoader;
import com.socket.edge.tester.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TestCaseRunner {

    private static final Logger log = LoggerFactory.getLogger(TestCaseRunner.class);

    private final Map<String, Map<String, String>>          stepContext    = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<IsoMessage>> pendingFutures = new LinkedHashMap<>();

    public TestResult run(TestCase tc) {
        return run(tc, Path.of(".").toAbsolutePath());
    }

    public TestResult run(TestCase tc, Path baseDir) {
        if (tc.getDataFile() != null) {
            return runDataDriven(tc, baseDir);
        }
        return runSingle(tc, tc.getVariables() != null ? tc.getVariables() : Map.of(), baseDir);
    }

    // =========================================================================
    // Data-driven loop
    // =========================================================================

    private TestResult runDataDriven(TestCase tc, Path baseDir) {
        TestResult aggregate = new TestResult(tc.getName());
        long t0 = System.currentTimeMillis();

        Path csvPath = baseDir.resolve(tc.getDataFile()).normalize();
        List<Map<String, String>> rows;
        try {
            rows = new CsvDataLoader().load(csvPath);
        } catch (Exception e) {
            aggregate.setStatus(TestResult.Status.ERROR);
            aggregate.setError("Cannot load dataFile: " + e.getMessage());
            log.error("Failed to load dataFile [{}]", csvPath, e);
            return aggregate;
        }

        if (rows.isEmpty()) {
            aggregate.setStatus(TestResult.Status.ERROR);
            aggregate.setError("dataFile has no data rows: " + csvPath);
            return aggregate;
        }

        System.out.printf("  Data file: %s (%d rows)%n", tc.getDataFile(), rows.size());

        List<TestResult> dataRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> csvRow = rows.get(i);

            // Priority: TC variables < CSV row (CSV overrides TC defaults)
            Map<String, String> mergedVars = new LinkedHashMap<>();
            if (tc.getVariables() != null) mergedVars.putAll(tc.getVariables());
            mergedVars.putAll(csvRow);

            stepContext.clear(); // reset context between rows
            TestResult rowResult = runSingle(tc, mergedVars, baseDir);
            rowResult.setDataRowIndex(i + 1);
            rowResult.setDataRowLabel(buildRowLabel(csvRow, i + 1));
            dataRows.add(rowResult);

            System.out.printf("    [Row %d/%d] %-40s → %s (%dms)%n",
                    i + 1, rows.size(),
                    rowResult.getDataRowLabel(),
                    rowResult.isPassed() ? "PASS" : "FAIL",
                    rowResult.getDurationMs());
        }

        aggregate.setDataRows(dataRows);
        aggregate.setDurationMs(System.currentTimeMillis() - t0);
        boolean allPassed = dataRows.stream().allMatch(TestResult::isPassed);
        aggregate.setStatus(allPassed ? TestResult.Status.PASSED : TestResult.Status.FAILED);
        computeLatencyStatsFromRows(aggregate, dataRows);
        return aggregate;
    }

    private String buildRowLabel(Map<String, String> row, int idx) {
        StringBuilder sb = new StringBuilder("Row " + idx + ": ");
        int count = 0;
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (count++ >= 3) break; // show at most 3 columns
            sb.append(e.getKey()).append("=").append(e.getValue()).append(" ");
        }
        return sb.toString().trim();
    }

    private void computeLatencyStatsFromRows(TestResult aggregate, List<TestResult> rows) {
        List<Long> all = rows.stream()
                .flatMap(r -> r.getSteps().stream())
                .filter(s -> s.getLatencyMs() > 0)
                .map(StepResult::getLatencyMs)
                .sorted()
                .collect(Collectors.toList());
        if (all.isEmpty()) return;
        long sum = all.stream().mapToLong(Long::longValue).sum();
        aggregate.setAvgLatencyMs(sum / all.size());
        aggregate.setMaxLatencyMs(all.get(all.size() - 1));
        int p95 = (int) Math.ceil(0.95 * all.size()) - 1;
        aggregate.setP95LatencyMs(all.get(Math.max(0, p95)));
    }

    // =========================================================================
    // Single run
    // =========================================================================

    private TestResult runSingle(TestCase tc, Map<String, String> vars, Path baseDir) {
        TestResult result = new TestResult(tc.getName());
        long t0 = System.currentTimeMillis();

        Map<String, IsoClient> clients = new LinkedHashMap<>();
        IsoServer server = null;

        try {
            server = setupServer(tc);
            setupClients(tc, vars, clients);

            boolean stopOnNextHardFail = false;
            for (TestStep step : tc.getSteps()) {
                if (stopOnNextHardFail) {
                    result.getSteps().add(StepResult.skipped(step));
                    continue;
                }
                StepResult sr = executeStep(step, vars, clients, baseDir);
                result.getSteps().add(sr);

                boolean hardFailed = sr.getStatus() == StepResult.Status.FAILED
                        || sr.getStatus() == StepResult.Status.ERROR;
                if (hardFailed && !step.isSkipOnFail()) stopOnNextHardFail = true;
            }

        } catch (Exception e) {
            result.setStatus(TestResult.Status.ERROR);
            result.setError(e.getMessage());
            log.error("Test case [{}] error", tc.getName(), e);
        } finally {
            teardown(tc, clients, server);
            result.setDurationMs(System.currentTimeMillis() - t0);
        }

        if (result.getStatus() == null) {
            boolean anyHardFail = result.getSteps().stream()
                    .anyMatch(s -> s.getStatus() == StepResult.Status.FAILED
                               || s.getStatus() == StepResult.Status.ERROR);
            result.setStatus(anyHardFail ? TestResult.Status.FAILED : TestResult.Status.PASSED);
        }

        computeLatencyStats(result);
        return result;
    }

    // =========================================================================
    // Setup / Teardown
    // =========================================================================

    private IsoServer setupServer(TestCase tc) throws Exception {
        TestCase.Setup setup = tc.getSetup();
        if (setup == null || setup.getServer() == null) return null;

        TestCase.ServerConfig sc = setup.getServer();
        IsoServer server = new IsoServer();
        server.start(sc.getPort(), sc.isAutoRespond(), sc.getDelayMs(),
                     sc.getResponseCode(), sc.getHeaderBytes());
        return server;
    }

    private void setupClients(TestCase tc, Map<String, String> vars,
                               Map<String, IsoClient> clients) throws Exception {
        TestCase.Setup setup = tc.getSetup();
        if (setup == null) return;

        // connections: list — named multi-connections
        if (setup.getConnections() != null) {
            for (TestCase.ConnectConfig cc : setup.getConnections()) {
                String id = cc.getId() != null && !cc.getId().isBlank() ? cc.getId() : "default";
                connectOne(id, cc, vars, clients);
            }
        }

        // connect: single (backward compat) — only if "default" not already created
        if (setup.getConnect() != null && !clients.containsKey("default")) {
            connectOne("default", setup.getConnect(), vars, clients);
        }
    }

    private void connectOne(String id, TestCase.ConnectConfig cc,
                             Map<String, String> vars, Map<String, IsoClient> clients) throws Exception {
        String host = resolve(cc.getHost(), vars);
        int    port = cc.getPort();
        IsoClient client = new IsoClient();
        client.connect(host, port, cc.getTimeoutMs(), cc.getHeaderBytes());
        clients.put(id, client);
    }

    private void teardown(TestCase tc, Map<String, IsoClient> clients, IsoServer server) {
        TestCase.Teardown td = tc.getTeardown();
        boolean doDisconnect = td == null || td.isDisconnect();
        boolean doStopServer = td == null || td.isStopServer();

        if (doDisconnect) {
            clients.forEach((id, client) -> {
                try { client.disconnect(); } catch (Exception e) {
                    log.warn("Error disconnecting '{}': {}", id, e.getMessage());
                }
            });
            clients.clear();
        }
        if (doStopServer && server != null) {
            try { server.stop(); } catch (Exception e) {
                log.warn("Error stopping mock server: {}", e.getMessage());
            }
        }
    }

    // =========================================================================
    // Step dispatch
    // =========================================================================

    private StepResult executeStep(TestStep step, Map<String, String> vars,
                                   Map<String, IsoClient> clients, Path baseDir) {
        StepResult sr = new StepResult();
        sr.setStepId(step.getId());
        sr.setStepName(step.getName() != null ? step.getName() : step.getId());

        try {
            switch (step.getAction()) {
                case SEND       -> executeSend(step, vars, clients, sr);
                case SEND_ASYNC -> executeSendAsync(step, vars, clients, sr);
                case AWAIT      -> executeAwait(step, vars, sr);
                case DISCONNECT -> executeDisconnect(step, clients, sr);
                case WAIT       -> Thread.sleep(step.getWaitMs()  != null ? step.getWaitMs()  : 0);
                case PAUSE      -> Thread.sleep(step.getPauseMs() != null ? step.getPauseMs() : 0);
                case LOG        -> {
                    log.info("[LOG] {}", resolve(step.getLogMessage(), vars));
                    System.out.println("[LOG] " + resolve(step.getLogMessage(), vars));
                    sr.setStatus(StepResult.Status.PASSED);
                }
                case CALL       -> executeCall(step, vars, clients, sr, baseDir);
            }
            if (sr.getStatus() == null) sr.setStatus(StepResult.Status.PASSED);
        } catch (Exception e) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError(e.getMessage());
            log.error("Step [{}] error: {}", step.getId(), e.getMessage());
        }

        return sr;
    }

    // =========================================================================
    // SEND
    // =========================================================================

    private void executeSend(TestStep step, Map<String, String> vars,
                             Map<String, IsoClient> clients, StepResult sr) throws Exception {
        String connId = step.getConnection();
        IsoClient client = clients.get(connId);
        if (client == null)
            throw new IllegalStateException("Connection '" + connId + "' not found. Available: " + clients.keySet());
        executeSendWithClient(step, vars, client, sr);
    }

    private void executeSendWithClient(TestStep step, Map<String, String> vars,
                             IsoClient client, StepResult sr) throws Exception {
        IsoMessage request = buildMessage(step.getMessage(), vars);
        sr.setRequest(request);
        recordContext(step.getId(), "request", request);

        long t0 = System.currentTimeMillis();
        IsoMessage response = client.send(request, 30000);
        long latency = System.currentTimeMillis() - t0;

        sr.setLatencyMs(latency);
        sr.setResponse(response);
        if (response != null) recordContext(step.getId(), "response", response);

        List<AssertionResult> results = evaluateAssertions(step, request, response, latency, vars);
        sr.setAssertions(results);

        boolean hardFailed = results.stream()
                .anyMatch(a -> !a.isPassed() && a.getSeverity() == Assertion.Severity.HARD);
        sr.setStatus(hardFailed ? StepResult.Status.FAILED : StepResult.Status.PASSED);
    }

    // =========================================================================
    // SEND_ASYNC — fire and forget, tidak blocking, tidak tunggu response
    // =========================================================================

    private void executeSendAsync(TestStep step, Map<String, String> vars,
                                   Map<String, IsoClient> clients, StepResult sr) {
        String connId = step.getConnection();
        IsoClient client = clients.get(connId);
        if (client == null) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError("Connection '" + connId + "' not found");
            return;
        }
        IsoMessage request = buildMessage(step.getMessage(), vars);
        sr.setRequest(request);
        recordContext(step.getId(), "request", request);

        // Fire — response captured for optional AWAIT step
        CompletableFuture<IsoMessage> future = client.sendAsync(request, 30000L)
              .whenComplete((resp, ex) -> {
                  if (ex != null) log.debug("SEND_ASYNC [{}] no response: {}", step.getId(), ex.getMessage());
                  else if (resp != null) recordContext(step.getId(), "response", resp);
              });

        pendingFutures.put(step.getId(), future);
        sr.setStatus(StepResult.Status.PASSED);
        log.info("SEND_ASYNC fired step '{}' via connection '{}'", step.getId(), connId);
    }

    // =========================================================================
    // AWAIT — tunggu response dari SEND_ASYNC, evaluasi assertions
    // =========================================================================

    private void executeAwait(TestStep step, Map<String, String> vars, StepResult sr) throws Exception {
        String refId = step.getAwaitStep();
        if (refId == null || refId.isBlank()) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError("AWAIT step missing 'awaitStep' field");
            return;
        }

        CompletableFuture<IsoMessage> future = pendingFutures.remove(refId);
        if (future == null) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError("No pending SEND_ASYNC found for awaitStep: '" + refId + "'");
            return;
        }

        long timeoutMs = step.getTimeoutMs() != null ? step.getTimeoutMs() : 30000L;
        long t0 = System.currentTimeMillis();

        IsoMessage response;
        try {
            response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            sr.setStatus(StepResult.Status.FAILED);
            sr.setError("AWAIT timeout after " + timeoutMs + "ms — no response for step '" + refId + "'");
            return;
        } catch (Exception e) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError("AWAIT error for step '" + refId + "': " + e.getMessage());
            return;
        }

        long latency = System.currentTimeMillis() - t0;
        sr.setLatencyMs(latency);
        sr.setResponse(response);

        // Retrieve original request from stepContext
        Map<String, String> reqCtx = stepContext.get(refId + ".request");
        if (reqCtx != null) {
            IsoMessage request = new IsoMessage(reqCtx.get("mti"));
            reqCtx.forEach((k, v) -> {
                if (k.startsWith("DE")) {
                    try { request.setField(Integer.parseInt(k.substring(2)), v); }
                    catch (NumberFormatException ignored) {}
                }
            });
            sr.setRequest(request);
        }

        // Evaluate assertions (using vars from calling TC)
        if (step.getAssertions() != null && !step.getAssertions().isEmpty()) {
            List<AssertionResult> results = evaluateAssertions(step, sr.getRequest(), response, latency, vars);
            sr.setAssertions(results);
            boolean hardFailed = results.stream()
                    .anyMatch(a -> !a.isPassed() && a.getSeverity() == Assertion.Severity.HARD);
            sr.setStatus(hardFailed ? StepResult.Status.FAILED : StepResult.Status.PASSED);
        } else {
            sr.setStatus(StepResult.Status.PASSED);
        }
    }

    // =========================================================================
    // DISCONNECT
    // =========================================================================

    private void executeDisconnect(TestStep step, Map<String, IsoClient> clients, StepResult sr) {
        String connId = step.getConnection();
        IsoClient client = clients.get(connId);
        if (client == null) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError("Connection '" + connId + "' not found or already disconnected");
            return;
        }
        try {
            client.disconnect();
            clients.remove(connId);
            log.info("Disconnected connection '{}'", connId);
            sr.setStatus(StepResult.Status.PASSED);
        } catch (Exception e) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError("Disconnect error: " + e.getMessage());
        }
    }

    // =========================================================================
    // CALL
    // =========================================================================

    private void executeCall(TestStep step, Map<String, String> callerVars,
                             Map<String, IsoClient> clients, StepResult sr, Path baseDir) throws Exception {
        String kwRef = step.getKeyword();
        if (kwRef == null || kwRef.isBlank()) {
            sr.setStatus(StepResult.Status.ERROR);
            sr.setError("CALL step missing 'keyword' field");
            return;
        }

        Path kwPath = resolveKeywordPath(kwRef, baseDir);
        Keyword kw = new YamlKeywordLoader().load(kwPath);

        // Variable priority: callerVars > CALL params > keyword defaults
        Map<String, String> mergedVars = new LinkedHashMap<>();
        if (kw.getParameters() != null) mergedVars.putAll(kw.getParameters());
        if (step.getParams()    != null) mergedVars.putAll(step.getParams());
        mergedVars.putAll(callerVars); // TC-level vars always win

        log.debug("CALL keyword '{}' from {}", kw.getName(), kwPath);

        List<StepResult> subResults = new ArrayList<>();
        boolean hardFailed = false;

        for (TestStep kwStep : kw.getSteps()) {
            if (hardFailed) {
                subResults.add(StepResult.skipped(kwStep));
                continue;
            }
            StepResult sub = executeStep(kwStep, mergedVars, clients, baseDir);
            subResults.add(sub);

            if ((sub.getStatus() == StepResult.Status.FAILED
                    || sub.getStatus() == StepResult.Status.ERROR)
                    && !kwStep.isSkipOnFail()) {
                hardFailed = true;
            }
        }

        sr.setSubSteps(subResults);
        sr.setStatus(hardFailed ? StepResult.Status.FAILED : StepResult.Status.PASSED);
        long totalLatency = subResults.stream().mapToLong(StepResult::getLatencyMs).sum();
        sr.setLatencyMs(totalLatency);
    }

    private Path resolveKeywordPath(String kwRef, Path baseDir) {
        // ends with .yaml → treat as relative path from baseDir
        if (kwRef.endsWith(".yaml") || kwRef.endsWith(".yml")) {
            return baseDir.resolve(kwRef).normalize();
        }
        // bare name → look in keywords/ subdirectory relative to baseDir
        return baseDir.resolve("keywords").resolve(kwRef + ".yaml").normalize();
    }

    // =========================================================================
    // Message builder
    // =========================================================================

    private IsoMessage buildMessage(TestStep.MessageDef def, Map<String, String> vars) {
        if (def == null) throw new IllegalArgumentException("SEND step missing 'message'");
        IsoMessage msg = new IsoMessage(resolve(def.getMti(), vars));
        if (def.getFields() != null) {
            def.getFields().forEach((key, raw) -> {
                int de = Integer.parseInt(key.toUpperCase().replace("DE", "").trim());
                msg.setField(de, resolve(raw, vars));
            });
        }
        return msg;
    }

    // =========================================================================
    // Assertion evaluator
    // =========================================================================

    private List<AssertionResult> evaluateAssertions(TestStep step, IsoMessage request,
                                                      IsoMessage response, long latencyMs,
                                                      Map<String, String> vars) {
        List<AssertionResult> results = new ArrayList<>();
        if (step.getAssertions() != null) {
            for (Assertion a : step.getAssertions()) {
                results.add(evaluate(a, request, response, latencyMs, vars));
            }
        }
        if (step.getLatencySla() != null) {
            LatencySla sla = step.getLatencySla();
            if (sla.getWarn() > 0) results.add(latencyResult(latencyMs, sla.getWarn(), Assertion.Severity.SOFT));
            if (sla.getFail() > 0) results.add(latencyResult(latencyMs, sla.getFail(), Assertion.Severity.HARD));
        }
        return results;
    }

    private AssertionResult evaluate(Assertion a, IsoMessage request, IsoMessage response,
                                     long latencyMs, Map<String, String> vars) {
        AssertionResult ar = new AssertionResult();
        ar.setSeverity(a.getSeverity() != null ? a.getSeverity() : Assertion.Severity.HARD);
        ar.setMessage(a.getMessage());

        if (a.getOnlyIf() != null) {
            String cond = resolve(a.getOnlyIf(), vars);
            if (!evalCondition(cond)) {
                ar.setPassed(true);
                ar.setSeverity(Assertion.Severity.INFO);
                ar.setDescription("Skipped (onlyIf not met)");
                return ar;
            }
        }

        if (a.getMti() != null) {
            String actual = response != null ? response.getMti() : "<no response>";
            ar.setDescription("MTI == " + a.getMti());
            ar.setActualValue(actual);
            ar.setExpectedValue(a.getMti());
            ar.setPassed(a.getMti().equals(actual));
            return ar;
        }

        if (a.getLatency() != null) {
            LatencySla sla  = a.getLatency();
            long limit      = sla.getFail() > 0 ? sla.getFail() : sla.getWarn();
            Assertion.Severity sev = sla.getFail() > 0 ? Assertion.Severity.HARD : Assertion.Severity.SOFT;
            ar.setSeverity(sev);
            ar.setDescription("Latency <= " + limit + "ms");
            ar.setActualValue(latencyMs + "ms");
            ar.setExpectedValue("<= " + limit + "ms");
            ar.setPassed(latencyMs <= limit);
            return ar;
        }

        if (a.getExpression() != null) {
            ar.setSeverity(Assertion.Severity.INFO);
            ar.setDescription("expression (Phase 2): " + a.getExpression());
            ar.setPassed(true);
            return ar;
        }

        if (a.getField() != null) return evaluateField(a, response, vars, ar);

        ar.setDescription("Unknown assertion type");
        ar.setPassed(false);
        return ar;
    }

    private AssertionResult evaluateField(Assertion a, IsoMessage response,
                                          Map<String, String> vars, AssertionResult ar) {
        int de     = Integer.parseInt(a.getField().toUpperCase().replace("DE", "").trim());
        String actual = response != null ? response.getField(de) : null;
        if (actual != null) actual = actual.trim();

        if (a.getEquals() != null) {
            String expected = resolve(a.getEquals(), vars);
            ar.setDescription("DE" + de + " == " + expected);
            ar.setActualValue(actual);
            ar.setExpectedValue(expected);
            ar.setPassed(expected.equals(actual));

        } else if (a.getIn() != null) {
            ar.setDescription("DE" + de + " in " + a.getIn());
            ar.setActualValue(actual);
            ar.setExpectedValue(a.getIn().toString());
            ar.setPassed(actual != null && a.getIn().contains(actual));

        } else if (a.getNotEquals() != null) {
            ar.setDescription("DE" + de + " != " + a.getNotEquals());
            ar.setActualValue(actual);
            ar.setExpectedValue("!= " + a.getNotEquals());
            ar.setPassed(!a.getNotEquals().equals(actual));

        } else if (Boolean.TRUE.equals(a.getNotEmpty())) {
            ar.setDescription("DE" + de + " notEmpty");
            ar.setActualValue(actual);
            ar.setExpectedValue("not empty");
            ar.setPassed(actual != null && !actual.isEmpty());

        } else if (a.getExists() != null) {
            boolean expectPresent = a.getExists();
            boolean actualPresent = response != null && response.hasField(de);
            ar.setDescription("DE" + de + (expectPresent ? " exists" : " not exists"));
            ar.setPassed(expectPresent == actualPresent);

        } else if (a.getMatches() != null) {
            ar.setDescription("DE" + de + " matches /" + a.getMatches() + "/");
            ar.setActualValue(actual);
            ar.setExpectedValue("/" + a.getMatches() + "/");
            ar.setPassed(actual != null && actual.matches(a.getMatches()));

        } else if (a.getEqualsField() != null) {
            String expected = resolve(a.getEqualsField(), vars);
            ar.setDescription("DE" + de + " == " + expected + "  [" + a.getEqualsField() + "]");
            ar.setActualValue(actual);
            ar.setExpectedValue(expected);
            ar.setPassed(expected.equals(actual));

        } else if (a.getGreaterThan() != null) {
            ar.setDescription("DE" + de + " > " + a.getGreaterThan());
            ar.setActualValue(actual);
            ar.setExpectedValue("> " + a.getGreaterThan());
            ar.setPassed(actual != null && actual.compareTo(a.getGreaterThan()) > 0);

        } else if (a.getBetween() != null) {
            String min = a.getBetween().getMin(), max = a.getBetween().getMax();
            ar.setDescription("DE" + de + " in [" + min + ", " + max + "]");
            ar.setActualValue(actual);
            ar.setExpectedValue("[" + min + ", " + max + "]");
            ar.setPassed(actual != null && actual.compareTo(min) >= 0 && actual.compareTo(max) <= 0);

        } else {
            ar.setDescription("DE" + de + " (no condition)");
            ar.setPassed(true);
        }

        return ar;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String resolve(String template, Map<String, String> vars) {
        if (template == null) return "";
        return TemplateFunctions.resolve(template, vars, stepContext);
    }

    private void recordContext(String stepId, String dir, IsoMessage msg) {
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("mti", msg.getMti());
        msg.getFields().forEach((de, v) -> ctx.put("DE" + de, v != null ? v.trim() : ""));
        stepContext.put(stepId + "." + dir, ctx);
    }

    private AssertionResult latencyResult(long actual, long limit, Assertion.Severity severity) {
        AssertionResult ar = new AssertionResult();
        ar.setSeverity(severity);
        ar.setDescription("Latency <= " + limit + "ms (" + severity.name().toLowerCase() + ")");
        ar.setActualValue(actual + "ms");
        ar.setExpectedValue("<= " + limit + "ms");
        ar.setPassed(actual <= limit);
        return ar;
    }

    private boolean evalCondition(String condition) {
        if (condition.contains("==")) {
            String[] p = condition.split("==", 2);
            return p[0].trim().equals(p[1].trim());
        }
        if (condition.contains("!=")) {
            String[] p = condition.split("!=", 2);
            return !p[0].trim().equals(p[1].trim());
        }
        return true;
    }

    private void computeLatencyStats(TestResult result) {
        List<Long> latencies = result.getSteps().stream()
                .filter(s -> s.getLatencyMs() > 0)
                .map(StepResult::getLatencyMs)
                .sorted()
                .collect(Collectors.toList());
        if (latencies.isEmpty()) return;

        long sum = latencies.stream().mapToLong(Long::longValue).sum();
        result.setAvgLatencyMs(sum / latencies.size());
        result.setMaxLatencyMs(latencies.get(latencies.size() - 1));
        int p95idx = (int) Math.ceil(0.95 * latencies.size()) - 1;
        result.setP95LatencyMs(latencies.get(Math.max(0, p95idx)));
    }
}
