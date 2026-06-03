package com.socket.edge.tester.runner;

import com.socket.edge.tester.loader.YamlTestSuiteLoader;
import com.socket.edge.tester.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class SuiteCollectionRunner {

    private static final Logger log = LoggerFactory.getLogger(SuiteCollectionRunner.class);

    public CollectionResult run(SuiteCollection collection, Path collectionDir) {
        CollectionResult result = new CollectionResult(collection.getName());
        long t0 = System.currentTimeMillis();

        List<String> suitePaths = collection.getSuites();
        int total = suitePaths.size();
        boolean stopped = false;

        for (int i = 0; i < total; i++) {
            String suitePath = suitePaths.get(i);

            System.out.printf("%n  ┌─ Suite [%d/%d]: %s%n", i + 1, total, suitePath);

            if (stopped) {
                SuiteResult skipped = new SuiteResult(suitePath);
                skipped.setStatus(SuiteResult.Status.ERROR);
                skipped.setError("Skipped (stopOnFail)");
                result.getSuites().add(skipped);
                System.out.printf("  └─ [SKIP] (stopOnFail)%n");
                continue;
            }

            Path resolvedPath = collectionDir.resolve(suitePath).normalize();
            SuiteResult suiteResult;
            try {
                TestSuite suite = new YamlTestSuiteLoader().load(resolvedPath);
                Path suiteDir    = resolvedPath.toAbsolutePath().getParent();
                suiteResult = new TestSuiteRunner().run(suite, suiteDir);
            } catch (Exception e) {
                suiteResult = new SuiteResult(suitePath);
                suiteResult.setStatus(SuiteResult.Status.ERROR);
                suiteResult.setError("Load failed: " + e.getMessage());
                log.error("Failed to load suite [{}]: {}", suitePath, e.getMessage());
            }

            result.getSuites().add(suiteResult);
            System.out.printf("  └─ %s %s — %d/%d cases  %dms%n",
                    suiteResult.isPassed() ? "[PASS]" : "[FAIL]",
                    suiteResult.getSuiteName(),
                    suiteResult.getPassedCases(), suiteResult.getTotalCases(),
                    suiteResult.getDurationMs());

            if (!suiteResult.isPassed() && collection.isStopOnFail()) {
                stopped = true;
            }
        }

        result.setDurationMs(System.currentTimeMillis() - t0);
        computeStatus(result);
        computeStats(result);
        return result;
    }

    private void computeStatus(CollectionResult result) {
        boolean anyBad = result.getSuites().stream()
                .anyMatch(s -> s.getStatus() != SuiteResult.Status.PASSED);
        result.setStatus(anyBad ? CollectionResult.Status.FAILED : CollectionResult.Status.PASSED);
    }

    private void computeStats(CollectionResult result) {
        List<Long> latencies = result.getSuites().stream()
                .filter(s -> s.getMaxLatencyMs() > 0)
                .map(SuiteResult::getMaxLatencyMs)
                .sorted()
                .collect(Collectors.toList());
        if (latencies.isEmpty()) return;

        long count = result.getSuites().stream().filter(s -> s.getAvgLatencyMs() > 0).count();
        if (count == 0) return;

        long sum = result.getSuites().stream()
                .filter(s -> s.getAvgLatencyMs() > 0)
                .mapToLong(SuiteResult::getAvgLatencyMs)
                .sum();

        result.setAvgLatencyMs(sum / count);
        result.setMaxLatencyMs(latencies.get(latencies.size() - 1));
        int p95 = (int) Math.ceil(0.95 * latencies.size()) - 1;
        result.setP95LatencyMs(latencies.get(Math.max(0, p95)));
    }
}
