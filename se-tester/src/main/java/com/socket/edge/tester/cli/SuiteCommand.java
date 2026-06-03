package com.socket.edge.tester.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.socket.edge.tester.loader.YamlTestSuiteLoader;
import com.socket.edge.tester.model.SuiteResult;
import com.socket.edge.tester.model.TestSuite;
import com.socket.edge.tester.report.JUnitXmlWriter;
import com.socket.edge.tester.runner.TestSuiteRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name        = "suite",
    description = "Run a test suite YAML file (multiple test cases).",
    mixinStandardHelpOptions = true
)
public class SuiteCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to suite YAML file")
    private Path suitePath;

    @Option(names = {"-o", "--output"}, description = "Output path (.xml = JUnit format)")
    private Path outputPath;

    @Option(names = {"--env"}, description = "Environment variables YAML file (key: value)")
    private Path envPath;

    @Option(names = {"-v", "--verbose"}, description = "Print full request/response fields per test case")
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        if (envPath != null) applyEnvFile(envPath);

        YamlTestSuiteLoader loader = new YamlTestSuiteLoader();
        TestSuite suite = loader.load(suitePath);
        Path suiteDir = suitePath.toAbsolutePath().getParent();

        System.out.println();
        System.out.println("Suite   : " + suite.getName());
        if (suite.getDescription() != null) System.out.println("         " + suite.getDescription());
        System.out.printf("Cases   : %d%n", suite.getTestCases() != null ? suite.getTestCases().size() : 0);
        if (suite.isStopOnFail()) System.out.println("Mode    : stopOnFail=true");
        System.out.println();

        TestSuiteRunner runner = new TestSuiteRunner();
        SuiteResult result = runner.run(suite, suiteDir);

        printSummary(result);

        if (outputPath != null) {
            String outName = outputPath.getFileName().toString().toLowerCase();
            if (outName.endsWith(".html")) {
                new com.socket.edge.tester.report.HtmlReportGenerator().writeSuite(result, outputPath);
                System.out.println("HTML report written to: " + outputPath);
            } else {
                new JUnitXmlWriter().writeSuite(result, outputPath);
                System.out.println("JUnit XML written to: " + outputPath);
            }
        }

        return result.isPassed() ? 0 : 1;
    }

    private void printSummary(SuiteResult result) {
        String border = result.isPassed()
                ? "==============================[ PASSED ]=============================="
                : "==============================[ FAILED ]==============================";
        System.out.println(border);
        System.out.printf("Suite  : %s%n", result.getSuiteName());
        System.out.printf("Time   : %dms%n", result.getDurationMs());
        System.out.printf("Cases  : %d total | %d passed | %d failed | %d error | %d skipped%n",
                result.getTotalCases(), result.getPassedCases(),
                result.getFailedCases(), result.getErrorCases(), result.getSkippedCases());
        if (result.getAvgLatencyMs() > 0) {
            System.out.printf("Latency: avg=%dms  p95=%dms  max=%dms%n",
                    result.getAvgLatencyMs(), result.getP95LatencyMs(), result.getMaxLatencyMs());
        }
        System.out.println();
    }

    @SuppressWarnings("unchecked")
    private void applyEnvFile(Path envPath) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> map = mapper.readValue(envPath.toFile(), Map.class);
            map.forEach((k, v) -> { if (v != null) System.setProperty(k, v.toString()); });
        } catch (Exception e) {
            System.err.println("Warning: could not load env file " + envPath + ": " + e.getMessage());
        }
    }
}
