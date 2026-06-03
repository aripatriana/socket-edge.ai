package com.socket.edge.tester.runner;

import com.socket.edge.tester.loader.YamlTestCaseLoader;
import com.socket.edge.tester.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class TestSuiteRunner {

    private static final Logger log = LoggerFactory.getLogger(TestSuiteRunner.class);

    public SuiteResult run(TestSuite suite, Path suiteDir) {
        SuiteResult result = new SuiteResult(suite.getName());
        long t0 = System.currentTimeMillis();

        List<String> tcPaths = suite.getTestCases();
        int total = tcPaths.size();
        boolean stopped = false;

        for (int i = 0; i < total; i++) {
            String tcPath = tcPaths.get(i);

            if (stopped) {
                TestResult skipped = new TestResult(tcPath);
                skipped.setStatus(TestResult.Status.SKIPPED);
                skipped.setDurationMs(0);
                result.getResults().add(skipped);
                System.out.printf("[%d/%d] [SKIP] %s  (stopOnFail)%n", i + 1, total, tcPath);
                continue;
            }

            System.out.printf("[%d/%d] Running: %s%n", i + 1, total, tcPath);

            Path resolvedPath = suiteDir.resolve(tcPath);
            TestResult tcResult;
            try {
                TestCase tc = new YamlTestCaseLoader().load(resolvedPath);
                tcResult = new TestCaseRunner().run(tc, resolvedPath.toAbsolutePath().getParent());
            } catch (Exception e) {
                tcResult = new TestResult(tcPath);
                tcResult.setStatus(TestResult.Status.ERROR);
                tcResult.setError("Load failed: " + e.getMessage());
                log.error("Failed to load [{}]: {}", tcPath, e.getMessage());
            }

            result.getResults().add(tcResult);
            printLine(tcResult, i + 1, total);

            if (!tcResult.isPassed() && suite.isStopOnFail()) {
                stopped = true;
            }
        }

        result.setDurationMs(System.currentTimeMillis() - t0);
        computeStatus(result);
        computeStats(result);
        return result;
    }

    private void printLine(TestResult r, int idx, int total) {
        String tag = switch (r.getStatus()) {
            case PASSED  -> "[PASS]";
            case FAILED  -> "[FAIL]";
            case ERROR   -> "[ERR ]";
            case SKIPPED -> "[SKIP]";
        };
        String latency = r.getAvgLatencyMs() > 0 ? "  avg=" + r.getAvgLatencyMs() + "ms" : "";
        System.out.printf("       %s %s%s%n", tag, r.getTestCaseName(), latency);
    }

    private void computeStatus(SuiteResult result) {
        boolean anyBad = result.getResults().stream()
                .anyMatch(r -> r.getStatus() == TestResult.Status.FAILED
                            || r.getStatus() == TestResult.Status.ERROR);
        result.setStatus(anyBad ? SuiteResult.Status.FAILED : SuiteResult.Status.PASSED);
    }

    private void computeStats(SuiteResult result) {
        List<Long> latencies = result.getResults().stream()
                .filter(r -> r.getAvgLatencyMs() > 0)
                .map(TestResult::getMaxLatencyMs)
                .sorted()
                .collect(Collectors.toList());
        if (latencies.isEmpty()) return;

        long sum = result.getResults().stream()
                .filter(r -> r.getAvgLatencyMs() > 0)
                .mapToLong(TestResult::getAvgLatencyMs)
                .sum();
        long count = result.getResults().stream().filter(r -> r.getAvgLatencyMs() > 0).count();

        result.setAvgLatencyMs(sum / count);
        result.setMaxLatencyMs(latencies.get(latencies.size() - 1));
        int p95idx = (int) Math.ceil(0.95 * latencies.size()) - 1;
        result.setP95LatencyMs(latencies.get(Math.max(0, p95idx)));
    }
}
