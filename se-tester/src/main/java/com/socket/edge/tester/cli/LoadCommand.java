package com.socket.edge.tester.cli;

import com.socket.edge.tester.model.LoadResult;
import com.socket.edge.tester.report.LoadReportWriter;
import com.socket.edge.tester.runner.LoadRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name        = "load",
    description = "Performance load test — fire sustained TPS against SE-Core and report latency percentiles.",
    mixinStandardHelpOptions = true
)
public class LoadCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target host")
    private String host;

    @Option(names = {"-p", "--port"}, required = true, description = "Target port")
    private int port;

    @Option(names = {"--tps"}, defaultValue = "10",
            description = "Target transactions per second (default: 10)")
    private int tps;

    @Option(names = {"-d", "--duration"}, defaultValue = "60",
            description = "Test duration in seconds (default: 60)")
    private int durationSec;

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
            description = "Warmup period in seconds, excluded from stats (default: 5)")
    private int warmupSec;

    @Option(names = {"--live"}, defaultValue = "5",
            description = "Live stats print interval in seconds (default: 5)")
    private int liveIntervalSec;

    @Option(names = {"-o", "--output"},
            description = "Output file: .csv for raw data, .html for full report")
    private String outputPath;

    @Override
    public Integer call() throws Exception {
        LoadRunner runner = new LoadRunner(host, port, tps, durationSec, connections,
                                           headerBytes, timeoutMs, warmupSec, liveIntervalSec);
        LoadResult result = runner.run();

        printSummary(result);

        if (outputPath != null) {
            Path out = Path.of(outputPath);
            if (outputPath.endsWith(".csv")) {
                LoadReportWriter.writeCsv(result, out);
                System.out.printf("[load] CSV report written: %s%n", out.toAbsolutePath());
            } else {
                LoadReportWriter.writeHtml(result, out);
                System.out.printf("[load] HTML report written: %s%n", out.toAbsolutePath());
            }
        }

        boolean hasErrors = result.getTotalError() > 0 || result.getTotalTimeout() > 0;
        return hasErrors ? 1 : 0;
    }

    private void printSummary(LoadResult r) {
        String border = "═".repeat(62);
        System.out.println("\n╔" + border + "╗");
        System.out.printf("║  %-60s║%n", "LOAD TEST RESULT");
        System.out.println("╠" + border + "╣");
        System.out.printf("║  %-30s %29s ║%n", "Achieved TPS",
            String.format("%.1f tps", r.getAchievedTps()));
        System.out.printf("║  %-30s %29s ║%n", "Total Sent",   r.getTotalSent());
        System.out.printf("║  %-30s %29s ║%n", "Success",      r.getTotalSuccess());
        System.out.printf("║  %-30s %29s ║%n", "Error",        r.getTotalError());
        System.out.printf("║  %-30s %29s ║%n", "Timeout",      r.getTotalTimeout());
        System.out.printf("║  %-30s %29s ║%n", "Success Rate",
            String.format("%.2f%%", r.getSuccessRate()));
        System.out.println("╠" + border + "╣");
        System.out.printf("║  %-30s %29s ║%n", "Latency Min",  r.getLatencyMin() + " ms");
        System.out.printf("║  %-30s %29s ║%n", "Latency Avg",  r.getLatencyAvg() + " ms");
        System.out.printf("║  %-30s %29s ║%n", "Latency P50",  r.getLatencyP50() + " ms");
        System.out.printf("║  %-30s %29s ║%n", "Latency P90",  r.getLatencyP90() + " ms");
        System.out.printf("║  %-30s %29s ║%n", "Latency P95",  r.getLatencyP95() + " ms");
        System.out.printf("║  %-30s %29s ║%n", "Latency P99",  r.getLatencyP99() + " ms");
        System.out.printf("║  %-30s %29s ║%n", "Latency Max",  r.getLatencyMax() + " ms");
        System.out.println("╚" + border + "╝\n");
    }
}
