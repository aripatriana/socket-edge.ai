package com.socket.edge.tester.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.socket.edge.tester.loader.YamlSuiteCollectionLoader;
import com.socket.edge.tester.model.CollectionResult;
import com.socket.edge.tester.model.SuiteCollection;
import com.socket.edge.tester.report.HtmlReportGenerator;
import com.socket.edge.tester.report.JUnitXmlWriter;
import com.socket.edge.tester.runner.SuiteCollectionRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name        = "collect",
    description = "Run a suite collection YAML (multiple suites in sequence).",
    mixinStandardHelpOptions = true
)
public class CollectCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to suite collection YAML file")
    private Path collectionPath;

    @Option(names = {"-o", "--output"}, description = "Output path (.xml = JUnit, .html = HTML report)")
    private Path outputPath;

    @Option(names = {"--env"}, description = "Environment variables YAML file")
    private Path envPath;

    @Override
    public Integer call() throws Exception {
        if (envPath != null) applyEnvFile(envPath);

        SuiteCollection collection = new YamlSuiteCollectionLoader().load(collectionPath);
        Path collectionDir = collectionPath.toAbsolutePath().getParent();

        System.out.println();
        System.out.println("Collection : " + collection.getName());
        if (collection.getDescription() != null) System.out.println("           " + collection.getDescription());
        System.out.printf("Suites     : %d%n", collection.getSuites() != null ? collection.getSuites().size() : 0);
        if (collection.isStopOnFail()) System.out.println("Mode       : stopOnFail=true");

        CollectionResult result = new SuiteCollectionRunner().run(collection, collectionDir);

        printSummary(result);

        if (outputPath != null) {
            String name = outputPath.getFileName().toString().toLowerCase();
            if (name.endsWith(".html")) {
                new HtmlReportGenerator().writeCollection(result, outputPath);
                System.out.println("HTML report written to: " + outputPath);
            } else {
                new JUnitXmlWriter().writeCollection(result, outputPath);
                System.out.println("JUnit XML written to: " + outputPath);
            }
        }

        return result.isPassed() ? 0 : 1;
    }

    private void printSummary(CollectionResult r) {
        String border = r.isPassed()
                ? "==============================[ PASSED ]=============================="
                : "==============================[ FAILED ]==============================";
        System.out.println("\n" + border);
        System.out.printf("Collection : %s%n", r.getCollectionName());
        System.out.printf("Time       : %,dms%n", r.getDurationMs());
        System.out.printf("Suites     : %d total | %d passed | %d failed%n",
                r.getTotalSuites(), r.getPassedSuites(), r.getFailedSuites());
        System.out.printf("Cases      : %d total | %d passed | %d failed | %d error | %d skipped%n",
                r.getTotalCases(), r.getPassedCases(), r.getFailedCases(),
                r.getErrorCases(), r.getSkippedCases());
        if (r.getAvgLatencyMs() > 0) {
            System.out.printf("Latency    : avg=%dms  p95=%dms  max=%dms%n",
                    r.getAvgLatencyMs(), r.getP95LatencyMs(), r.getMaxLatencyMs());
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
