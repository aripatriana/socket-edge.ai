package com.socket.edge.tester.runner;

import com.socket.edge.tester.core.client.IsoClient;
import com.socket.edge.tester.core.iso.IsoMessage;
import com.socket.edge.tester.model.LoadResult;
import com.socket.edge.tester.model.TransactionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class LoadRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadRunner.class);

    private final String host;
    private final int port;
    private final int targetTps;
    private final int durationSec;
    private final int connections;
    private final int headerBytes;
    private final long timeoutMs;
    private final int warmupSec;
    private final int liveIntervalSec;

    public LoadRunner(String host, int port, int targetTps, int durationSec,
                      int connections, int headerBytes, long timeoutMs,
                      int warmupSec, int liveIntervalSec) {
        this.host             = host;
        this.port             = port;
        this.targetTps        = targetTps;
        this.durationSec      = durationSec;
        this.connections      = connections;
        this.headerBytes      = headerBytes;
        this.timeoutMs        = timeoutMs;
        this.warmupSec        = warmupSec;
        this.liveIntervalSec  = liveIntervalSec;
    }

    public LoadResult run() throws Exception {
        // ── connect pool ──────────────────────────────────────────────────────
        printLine("Connecting %d connection(s) to %s:%d ...", connections, host, port);
        List<IsoClient> pool = new ArrayList<>(connections);
        for (int i = 0; i < connections; i++) {
            IsoClient c = new IsoClient();
            c.connect(host, port, 5000, headerBytes);
            pool.add(c);
        }
        printLine("Connected. Warming up %ds then running %d TPS × %ds",
                  warmupSec, targetTps, durationSec);

        // ── shared state ──────────────────────────────────────────────────────
        AtomicLong txCounter    = new AtomicLong(1);
        AtomicInteger connIndex = new AtomicInteger(0);
        AtomicLong sentCount    = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount   = new AtomicLong(0);
        AtomicLong timeoutCount = new AtomicLong(0);
        ConcurrentLinkedQueue<TransactionRecord> records = new ConcurrentLinkedQueue<>();

        // ── live stats printer ────────────────────────────────────────────────
        long startMs     = System.currentTimeMillis();
        long warmupEndMs = startMs + warmupSec * 1000L;

        ScheduledExecutorService statsPrinter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "load-stats");
            t.setDaemon(true);
            return t;
        });
        AtomicLong lastSent    = new AtomicLong(0);
        AtomicLong lastSuccess = new AtomicLong(0);
        statsPrinter.scheduleAtFixedRate(() -> {
            long elapsed = (System.currentTimeMillis() - startMs) / 1000;
            long nowSent = sentCount.get();
            long nowOk   = successCount.get();
            long deltaSent = nowSent - lastSent.getAndSet(nowSent);
            long deltaOk   = nowOk   - lastSuccess.getAndSet(nowOk);
            boolean inWarmup = System.currentTimeMillis() < warmupEndMs;
            String phase = inWarmup ? " [WARMUP]" : "";
            double intervalTps = (double) deltaSent / liveIntervalSec;
            printLine("[%02d:%02d]%s sent=%d ok=%d err=%d tmo=%d interval-tps=%.1f",
                      elapsed / 60, elapsed % 60, phase,
                      nowSent, nowOk, errorCount.get(), timeoutCount.get(),
                      intervalTps);
        }, liveIntervalSec, liveIntervalSec, TimeUnit.SECONDS);

        // ── rate-limited main loop ─────────────────────────────────────────────
        // Each tick = 1 / targetTps seconds. Use LockSupport for sub-ms precision.
        long intervalNanos   = 1_000_000_000L / targetTps;
        long warmupEndNanos  = System.nanoTime() + warmupSec * 1_000_000_000L;
        long testEndNanos    = warmupEndNanos + durationSec * 1_000_000_000L;
        long nextSendNanos   = System.nanoTime();

        while (true) {
            long now = System.nanoTime();
            if (now >= testEndNanos) break;

            if (now < nextSendNanos) {
                long parkNs = nextSendNanos - now;
                if (parkNs > 200_000L) {
                    LockSupport.parkNanos(parkNs - 100_000L);
                }
                continue;
            }
            nextSendNanos += intervalNanos;

            boolean measure = System.nanoTime() >= warmupEndNanos;

            // round-robin connection
            IsoClient client = pool.get((int)(connIndex.getAndIncrement() % connections));
            if (!client.isConnected()) continue;

            long txId = txCounter.getAndIncrement();
            IsoMessage msg = buildMessage(txId);

            sentCount.incrementAndGet();
            long sentNanos = System.nanoTime();

            client.sendAsync(msg, timeoutMs).whenComplete((resp, err) -> {
                long latencyMs = (System.nanoTime() - sentNanos) / 1_000_000L;
                String status;
                if (err != null) {
                    if (err instanceof TimeoutException || err.getCause() instanceof TimeoutException) {
                        status = "TIMEOUT";
                        timeoutCount.incrementAndGet();
                    } else {
                        status = "ERROR";
                        errorCount.incrementAndGet();
                    }
                } else {
                    status = "SUCCESS";
                    successCount.incrementAndGet();
                }
                if (measure) {
                    records.add(new TransactionRecord(System.currentTimeMillis(), latencyMs, status));
                }
            });
        }

        // ── drain in-flight ───────────────────────────────────────────────────
        Thread.sleep(Math.min(timeoutMs + 500L, 15_000L));

        statsPrinter.shutdown();
        for (IsoClient c : pool) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }

        // ── compute result ────────────────────────────────────────────────────
        return computeResult(records, startMs);
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
        msg.setField(41, "SELOAD01");
        msg.setField(42, "SE_LOAD_TEST   ");
        msg.setField(49, "360");
        return msg;
    }

    // ── stats computation ─────────────────────────────────────────────────────

    private LoadResult computeResult(ConcurrentLinkedQueue<TransactionRecord> queue, long startMs) {
        List<TransactionRecord> list = new ArrayList<>(queue);

        int totalSuccess = 0, totalError = 0, totalTimeout = 0;
        for (TransactionRecord r : list) {
            switch (r.getStatus()) {
                case "SUCCESS" -> totalSuccess++;
                case "ERROR"   -> totalError++;
                case "TIMEOUT" -> totalTimeout++;
            }
        }
        int totalSent = list.size();

        long[] latencies = list.stream()
            .filter(r -> "SUCCESS".equals(r.getStatus()))
            .mapToLong(TransactionRecord::getLatencyMs)
            .sorted()
            .toArray();

        long min = 0, avg = 0, max = 0, p50 = 0, p90 = 0, p95 = 0, p99 = 0;
        if (latencies.length > 0) {
            min = latencies[0];
            max = latencies[latencies.length - 1];
            avg = Arrays.stream(latencies).sum() / latencies.length;
            p50 = percentile(latencies, 50);
            p90 = percentile(latencies, 90);
            p95 = percentile(latencies, 95);
            p99 = percentile(latencies, 99);
        }

        double achievedTps = durationSec > 0 ? (double) totalSent / durationSec : 0.0;

        return new LoadResult(startMs, durationSec * 1000L,
                              totalSent, totalSuccess, totalError, totalTimeout,
                              achievedTps,
                              min, avg, max, p50, p90, p95, p99,
                              list);
    }

    private long percentile(long[] sorted, int pct) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    private void printLine(String fmt, Object... args) {
        System.out.printf("[load] " + fmt + "%n", args);
    }
}
