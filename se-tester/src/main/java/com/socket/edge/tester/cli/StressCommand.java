package com.socket.edge.tester.cli;

import com.socket.edge.tester.model.StressResult;
import com.socket.edge.tester.model.StressStepResult;
import com.socket.edge.tester.report.StressReportWriter;
import com.socket.edge.tester.runner.StressRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name        = "stress",
    description = "Ramp-up stress test — escalate TPS each step until breaking point is found.",
    mixinStandardHelpOptions = true
)
public class StressCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target host")
    private String host;

    @Option(names = {"-p", "--port"}, required = true, description = "Target port")
    private int port;

    @Option(names = {"--start-tps"}, defaultValue = "10",
            description = "Starting TPS (default: 10)")
    private int startTps;

    @Option(names = {"--max-tps"}, defaultValue = "500",
            description = "Maximum TPS cap (default: 500)")
    private int maxTps;

    @Option(names = {"--step"}, defaultValue = "10",
            description = "TPS increment per step (default: 10)")
    private int stepTps;

    @Option(names = {"--step-duration"}, defaultValue = "10",
            description = "Duration per step in seconds (default: 10)")
    private int stepDurationSec;

    @Option(names = {"-c", "--connections"}, defaultValue = "1",
            description = "Concurrent connections (default: 1)")
    private int connections;

    @Option(names = {"--header-bytes"}, defaultValue = "4",
            description = "Frame header size: 2=SE-Core, 4=standalone (default: 4)")
    private int headerBytes;

    @Option(names = {"--timeout-ms"}, defaultValue = "5000",
            description = "Per-transaction timeout in ms (default: 5000)")
    private long timeoutMs;

    @Option(names = {"--warmup"}, defaultValue = "5",
            description = "Warmup at start-tps before ramp begins in seconds (default: 5)")
    private int warmupSec;

    @Option(names = {"--error-threshold"}, defaultValue = "5.0",
            description = "Error rate %% to declare breaking point (default: 5.0)")
    private double errorThreshold;

    @Option(names = {"-o", "--output"},
            description = "Output HTML report file")
    private String outputPath;

    @Override
    public Integer call() throws Exception {
        StressRunner runner = new StressRunner(host, port,
                                               startTps, maxTps, stepTps, stepDurationSec,
                                               connections, headerBytes, timeoutMs,
                                               warmupSec, errorThreshold);
        StressResult result = runner.run();

        printSummary(result);

        if (outputPath != null) {
            StressReportWriter.writeHtml(result, Path.of(outputPath), stepDurationSec);
            System.out.printf("[stress] HTML report written: %s%n",
                              Path.of(outputPath).toAbsolutePath());
        }

        return result.isBreakingPointFound() ? 1 : 0;
    }

    private void printSummary(StressResult r) {
        String border = "═".repeat(62);
        System.out.println("\n╔" + border + "╗");
        System.out.printf("║  %-60s║%n", "STRESS TEST RESULT");
        System.out.println("╠" + border + "╣");
        System.out.printf("║  %-35s %24s ║%n", "Steps Completed", r.getSteps().size());
        System.out.printf("║  %-35s %24s ║%n", "Max Sustainable TPS",
            r.getMaxSustainableTps() + " TPS");

        if (r.isBreakingPointFound()) {
            StressStepResult bp = r.getSteps().stream()
                .filter(StressStepResult::isBreakingPoint).findFirst().orElse(null);
            if (bp != null) {
                System.out.printf("║  %-35s %24s ║%n", "Breaking Point",
                    bp.getTargetTps() + " TPS (err=" + String.format("%.1f%%", bp.getErrorRate()) + ")");
            }
        } else {
            System.out.printf("║  %-35s %24s ║%n", "Breaking Point", "Not reached");
        }

        // best stats at peak stable step
        r.getSteps().stream()
            .filter(s -> !s.isBreakingPoint())
            .reduce((a, b) -> b)
            .ifPresent(peak -> {
                System.out.println("╠" + border + "╣");
                System.out.printf("║  %-35s %24s ║%n", "Peak Stable TPS", peak.getTargetTps() + " TPS");
                System.out.printf("║  %-35s %24s ║%n", "  P50 latency", peak.getLatencyP50() + " ms");
                System.out.printf("║  %-35s %24s ║%n", "  P95 latency", peak.getLatencyP95() + " ms");
                System.out.printf("║  %-35s %24s ║%n", "  P99 latency", peak.getLatencyP99() + " ms");
                System.out.printf("║  %-35s %24s ║%n", "  Success rate",
                    String.format("%.2f%%", 100.0 - peak.getErrorRate()));
            });

        System.out.println("╚" + border + "╝\n");
    }
}
