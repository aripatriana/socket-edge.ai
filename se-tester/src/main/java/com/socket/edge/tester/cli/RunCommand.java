package com.socket.edge.tester.cli;

import com.socket.edge.tester.loader.YamlTestCaseLoader;
import com.socket.edge.tester.model.*;
import com.socket.edge.tester.report.JUnitXmlWriter;
import com.socket.edge.tester.runner.TestCaseRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name        = "run",
    description = "Run a single test case YAML file against Socket Edge.",
    mixinStandardHelpOptions = true
)
public class RunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to test case YAML file")
    private Path testCasePath;

    @Option(names = {"-o", "--output"}, description = "Output path (.xml = JUnit format)")
    private Path outputPath;

    @Option(names = {"--env"}, description = "Environment variables YAML file (key: value)")
    private Path envPath;

    @Option(names = {"-v", "--verbose"}, description = "Print full request/response fields")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        // Load env file first (so {{env.*}} resolves correctly)
        if (envPath != null) applyEnvFile(envPath);

        YamlTestCaseLoader loader = new YamlTestCaseLoader();
        TestCase tc = loader.load(testCasePath);

        System.out.println("\nRunning: " + tc.getName());
        if (tc.getDescription() != null) System.out.println("         " + tc.getDescription());
        System.out.println();

        TestCaseRunner runner = new TestCaseRunner();
        TestResult result = runner.run(tc, testCasePath.toAbsolutePath().getParent());

        printResult(result);

        if (outputPath != null) writeOutput(result, outputPath);

        // Exit code: 0 = PASSED, 1 = FAILED/ERROR
        return result.isPassed() ? 0 : 1;
    }

    // -------------------------------------------------------------------------
    // Console output
    // -------------------------------------------------------------------------

    private void printResult(TestResult result) {
        String status = result.isPassed() ? "PASSED" : "FAILED";
        String border = result.isPassed()
                ? "==============================[ PASSED ]=============================="
                : "==============================[ FAILED ]==============================";

        System.out.println(border);
        System.out.printf("Test : %s%n", result.getTestCaseName());
        System.out.printf("Time : %dms%n", result.getDurationMs());
        if (result.isDataDriven()) {
            long rowsPassed = result.getDataRows().stream().filter(TestResult::isPassed).count();
            System.out.printf("Rows : %d total | %d passed | %d failed%n",
                    result.getDataRows().size(), rowsPassed,
                    result.getDataRows().size() - rowsPassed);
        } else {
            System.out.printf("Steps: %d total | %d passed | %d failed%n",
                    result.getTotalSteps(), result.getPassedSteps(), result.getFailedSteps());
        }
        if (result.getAvgLatencyMs() > 0) {
            System.out.printf("Latency: avg=%dms  p95=%dms  max=%dms%n",
                    result.getAvgLatencyMs(), result.getP95LatencyMs(), result.getMaxLatencyMs());
        }
        System.out.println();

        if (result.isDataDriven()) {
            for (TestResult row : result.getDataRows()) {
                String rowStatus = row.isPassed() ? "[PASS]" : "[FAIL]";
                System.out.printf("  %s %s (%dms)%n", rowStatus, row.getDataRowLabel(), row.getDurationMs());
                if (verbose) {
                    for (StepResult sr : row.getSteps()) printStep(sr);
                }
            }
        } else {
            for (StepResult sr : result.getSteps()) printStep(sr);
        }

        if (result.getError() != null) {
            System.out.println("  ERROR: " + result.getError());
        }
        System.out.println();
    }

    private void printStep(StepResult sr) {
        String icon = switch (sr.getStatus()) {
            case PASSED  -> "[PASS]";
            case FAILED  -> "[FAIL]";
            case SKIPPED -> "[SKIP]";
            case ERROR   -> "[ERR ]";
        };
        System.out.printf("  %s %s", icon, sr.getStepName());
        if (sr.getLatencyMs() > 0) System.out.printf(" (%dms)", sr.getLatencyMs());
        System.out.println();

        if (verbose) {
            if (sr.getRequest()  != null) printFields("    >> ", sr.getRequest());
            if (sr.getResponse() != null) printFields("    << ", sr.getResponse());
        }

        if (sr.getAssertions() != null) {
            for (AssertionResult ar : sr.getAssertions()) {
                if (ar.getSeverity() == Assertion.Severity.INFO) continue;
                String mark = ar.isPassed() ? "  [OK]" : "  [NOT]";
                System.out.printf("       %s %s%n", mark, ar.getDescription());
                if (!ar.isPassed()) {
                    System.out.printf("           expected: %s%n", ar.getExpectedValue());
                    System.out.printf("           actual  : %s%n", ar.getActualValue());
                    if (ar.getMessage() != null) System.out.printf("           message : %s%n", ar.getMessage());
                }
            }
        }

        if (sr.getSubSteps() != null && !sr.getSubSteps().isEmpty()) {
            for (StepResult sub : sr.getSubSteps()) {
                System.out.print("    ");
                printStep(sub);
            }
        }

        if (sr.getError() != null) {
            System.out.printf("       Error: %s%n", sr.getError());
        }
    }

    private void printFields(String prefix, com.socket.edge.tester.core.iso.IsoMessage msg) {
        System.out.println(prefix + "MTI=" + msg.getMti());
        msg.getFields().forEach((de, v) ->
                System.out.printf("%s DE%d=%s%n", prefix, de, de == 2 ? maskPan(v) : v));
    }

    private static String maskPan(String v) {
        if (v == null || v.length() < 10) return v;
        return v.substring(0, 6) + "******" + v.substring(v.length() - 4);
    }

    // -------------------------------------------------------------------------
    // Output writers
    // -------------------------------------------------------------------------

    private void writeOutput(TestResult result, Path path) throws Exception {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".xml")) {
            new JUnitXmlWriter().write(List.of(result), path);
            System.out.println("JUnit XML written to: " + path);
        } else if (name.endsWith(".html")) {
            new com.socket.edge.tester.report.HtmlReportGenerator().writeTestCase(result, path);
            System.out.println("HTML report written to: " + path);
        } else {
            System.out.println("Warning: unsupported output format for " + path + " (use .xml or .html)");
        }
    }

    // -------------------------------------------------------------------------
    // Env file loader
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void applyEnvFile(Path envPath) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper(
                            new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
            java.util.Map<String, Object> map = mapper.readValue(envPath.toFile(), java.util.Map.class);
            map.forEach((k, v) -> {
                if (v != null) System.setProperty(k, v.toString());
            });
        } catch (Exception e) {
            System.err.println("Warning: could not load env file " + envPath + ": " + e.getMessage());
        }
    }
}