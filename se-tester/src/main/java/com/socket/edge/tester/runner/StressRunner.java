package com.socket.edge.tester.runner;

import com.socket.edge.tester.core.client.IsoClient;
import com.socket.edge.tester.core.iso.IsoMessage;
import com.socket.edge.tester.model.StressResult;
import com.socket.edge.tester.model.StressStepResult;
import com.socket.edge.tester.model.TransactionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class StressRunner {

    private static final Logger log = LoggerFactory.getLogger(StressRunner.class);

    private final String host;
    private final int port;
    private final int startTps;
    private final int maxTps;
    private final int stepTps;
    private final int stepDurationSec;
    private final int connections;
    private final int headerBytes;
    private final long timeoutMs;
    private final int warmupSec;
    private final double errorThreshold;

    private final AtomicLong txCounter = new AtomicLong(1);

    public StressRunner(String host, int port,
                        int startTps, int maxTps, int stepTps, int stepDurationSec,
                        int connections, int headerBytes, long timeoutMs,
                        int warmupSec, double errorThreshold) {
        this.host             = host;
        this.port             = port;
        this.startTps         = startTps;
        this.maxTps           = maxTps;
        this.stepTps          = stepTps;
        this.stepDurationSec  = stepDurationSec;
        this.connections      = connections;
        this.headerBytes      = headerBytes;
        this.timeoutMs        = timeoutMs;
        this.warmupSec        = warmupSec;
        this.errorThreshold   = errorThreshold;
    }

    public StressResult run() throws Exception {
        long startTimeMs = System.currentTimeMillis();

        // ── connect pool ──────────────────────────────────────────────────────
        printLine("Connecting %d connection(s) to %s:%d ...", connections, host, port);
        List<IsoClient> pool = new ArrayList<>(connections);
        for (int i = 0; i < connections; i++) {
            IsoClient c = new IsoClient();
            c.connect(host, port, 5000, headerBytes);
            pool.add(c);
        }

        // ── warmup ────────────────────────────────────────────────────────────
        if (warmupSec > 0) {
            printLine("Warming up at %d TPS for %ds ...", startTps, warmupSec);
            fireStep(pool, startTps, warmupSec);
        }

        // ── ramp-up steps ─────────────────────────────────────────────────────
        printLine("Ramp-up: %d → %d TPS, step=%d, step-dur=%ds, err-threshold=%.1f%%",
                  startTps, maxTps, stepTps, stepDurationSec, errorThreshold);
        printStepHeader();

        List<StressStepResult> steps = new ArrayList<>();
        boolean breakingPointFound = false;
        int stepNum = 0;

        for (int tps = startTps; tps <= maxTps; tps += stepTps) {
            stepNum++;
            List<TransactionRecord> records = fireStep(pool, tps, stepDurationSec);

            StressStepResult sr = computeStep(stepNum, tps, records, false);

            boolean isBreak = sr.getErrorRate() > errorThreshold;
            if (isBreak) {
                sr = computeStep(stepNum, tps, records, true);
                steps.add(sr);
                printStepRow(sr, stepDurationSec);
                breakingPointFound = true;
                break;
            }

            steps.add(sr);
            printStepRow(sr, stepDurationSec);
        }

        // ── disconnect ────────────────────────────────────────────────────────
        for (IsoClient c : pool) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }

        int maxSustainableTps = steps.stream()
            .filter(s -> !s.isBreakingPoint())
            .mapToInt(StressStepResult::getTargetTps)
            .max().orElse(0);

        return new StressResult(steps, maxSustainableTps, breakingPointFound, startTimeMs);
    }

    // ── step execution ────────────────────────────────────────────────────────

    private List<TransactionRecord> fireStep(List<IsoClient> pool, int targetTps, int durationSec)
            throws InterruptedException {
        ConcurrentLinkedQueue<TransactionRecord> records = new ConcurrentLinkedQueue<>();
        AtomicInteger connIndex = new AtomicInteger(0);

        long intervalNanos = 1_000_000_000L / targetTps;
        long endNanos      = System.nanoTime() + durationSec * 1_000_000_000L;
        long nextSendNanos = System.nanoTime();

        while (true) {
            long now = System.nanoTime();
            if (now >= endNanos) break;

            if (now < nextSendNanos) {
                long parkNs = nextSendNanos - now;
                if (parkNs > 200_000L) LockSupport.parkNanos(parkNs - 100_000L);
                continue;
            }
            nextSendNanos += intervalNanos;

            IsoClient client = pool.get((int)(connIndex.getAndIncrement() % connections));
            if (!client.isConnected()) continue;

            long txId = txCounter.getAndIncrement();
            IsoMessage msg = buildMessage(txId);
            long sentNanos = System.nanoTime();

            client.sendAsync(msg, timeoutMs).whenComplete((resp, err) -> {
                long latencyMs = (System.nanoTime() - sentNanos) / 1_000_000L;
                String status;
                if (err != null) {
                    boolean isTimeout = err instanceof TimeoutException
                        || (err.getCause() instanceof TimeoutException);
                    status = isTimeout ? "TIMEOUT" : "ERROR";
                } else {
                    status = "SUCCESS";
                }
                records.add(new TransactionRecord(System.currentTimeMillis(), latencyMs, status));
            });
        }

        // drain in-flight for this step
        Thread.sleep(Math.min(timeoutMs + 200L, 6_000L));
        return new ArrayList<>(records);
    }

    // ── stats ─────────────────────────────────────────────────────────────────

    private StressStepResult computeStep(int stepNum, int targetTps,
                                         List<TransactionRecord> records, boolean isBreak) {
        int sent = records.size();
        int ok = 0, err = 0, tmo = 0;
        for (TransactionRecord r : records) {
            switch (r.getStatus()) {
                case "SUCCESS" -> ok++;
                case "ERROR"   -> err++;
                case "TIMEOUT" -> tmo++;
            }
        }

        long[] lat = records.stream()
            .filter(r -> "SUCCESS".equals(r.getStatus()))
            .mapToLong(TransactionRecord::getLatencyMs)
            .sorted().toArray();

        long min = 0, avg = 0, max = 0, p50 = 0, p90 = 0, p95 = 0, p99 = 0;
        if (lat.length > 0) {
            min = lat[0];
            max = lat[lat.length - 1];
            avg = Arrays.stream(lat).sum() / lat.length;
            p50 = pct(lat, 50);
            p90 = pct(lat, 90);
            p95 = pct(lat, 95);
            p99 = pct(lat, 99);
        }

        return new StressStepResult(stepNum, targetTps, sent, ok, err, tmo,
                                    min, avg, max, p50, p90, p95, p99, isBreak);
    }

    private long pct(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    // ── message builder ───────────────────────────────────────────────────────

    private static final DateTimeFormatter DTF_TRANS = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter DTF_TIME  = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DTF_DATE  = DateTimeFormatter.ofPattern("MMdd");

    private IsoMessage buildMessage(long txId) {
        LocalDateTime now = LocalDateTime.now();
        String stan = String.format("%06d", (txId % 999999) + 1);
        String rrn  = String.format("%012d", txId % 1_000_000_000_000L);

        IsoMessage msg = new IsoMessage("0200");
        msg.setField(2,  "4000123456789010");
        msg.setField(3,  "000000");
        msg.setField(4,  "000000000100");
        msg.setField(7,  now.format(DTF_TRANS));
        msg.setField(11, stan);
        msg.setField(12, now.format(DTF_TIME));
        msg.setField(13, now.format(DTF_DATE));
        msg.setField(22, "051");
        msg.setField(37, rrn);
        msg.setField(41, "SESTRES01");
        msg.setField(42, "SE_STRESS_TEST ");
        msg.setField(49, "360");
        return msg;
    }

    // ── console output ────────────────────────────────────────────────────────

    private void printStepHeader() {
        System.out.printf("%-6s %-8s %-8s %-8s %-6s %-6s %-8s %-8s %-8s %-8s %-8s%n",
            "Step", "TPS", "Sent", "OK", "Err", "Tmo", "p50(ms)", "p90(ms)", "p95(ms)", "p99(ms)", "Status");
        System.out.println("─".repeat(90));
    }

    private void printStepRow(StressStepResult s, int stepSec) {
        double achTps = stepSec > 0 ? (double) s.getTotalSent() / stepSec : 0;
        String status = s.isBreakingPoint()
            ? String.format("✗ BREAK (%.1f%%)", s.getErrorRate())
            : String.format("✓ (%.1f%%)", s.getErrorRate());
        System.out.printf("%-6d %-8.1f %-8d %-8d %-6d %-6d %-8d %-8d %-8d %-8d %-8s%n",
            s.getStepNum(), achTps, s.getTotalSent(), s.getTotalSuccess(),
            s.getTotalError(), s.getTotalTimeout(),
            s.getLatencyP50(), s.getLatencyP90(), s.getLatencyP95(), s.getLatencyP99(),
            status);
    }

    private void printLine(String fmt, Object... args) {
        System.out.printf("[stress] " + fmt + "%n", args);
    }
}
