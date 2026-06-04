package com.socket.edge.tester.report;

import com.socket.edge.tester.model.LoadResult;
import com.socket.edge.tester.model.TransactionRecord;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class LoadReportWriter {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** Write CSV: timestamp_ms,latency_ms,status */
    public static void writeCsv(LoadResult result, Path path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("timestamp_ms,latency_ms,status");
            for (TransactionRecord r : result.getRecords()) {
                pw.printf("%d,%d,%s%n", r.getTimestampMs(), r.getLatencyMs(), r.getStatus());
            }
        }
    }

    /** Write self-contained HTML load report. */
    public static void writeHtml(LoadResult result, Path path) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println(buildHtml(result));
        }
    }

    // ── HTML builder ─────────────────────────────────────────────────────────

    private static String buildHtml(LoadResult r) {
        StringBuilder sb = new StringBuilder();
        String startTs = TS_FMT.format(Instant.ofEpochMilli(r.getStartTimeMs()));
        double errorRate = r.getTotalSent() == 0 ? 0.0
            : (double)(r.getTotalError() + r.getTotalTimeout()) / r.getTotalSent() * 100.0;

        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>SE-Tester Load Report</title>
            <style>
              *{box-sizing:border-box;margin:0;padding:0}
              body{font-family:'Segoe UI',Arial,sans-serif;background:#0f1117;color:#e2e8f0;padding:24px}
              h1{font-size:1.5rem;font-weight:700;color:#63b3ed;margin-bottom:4px}
              .subtitle{font-size:.85rem;color:#718096;margin-bottom:24px}
              .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:24px}
              .card{background:#1a1d27;border:1px solid #2d3748;border-radius:8px;padding:16px}
              .card .label{font-size:.75rem;color:#718096;text-transform:uppercase;letter-spacing:.05em}
              .card .value{font-size:1.6rem;font-weight:700;margin-top:4px}
              .ok{color:#68d391}.warn{color:#f6ad55}.err{color:#fc8181}.info{color:#63b3ed}
              table{width:100%;border-collapse:collapse;background:#1a1d27;border:1px solid #2d3748;border-radius:8px;overflow:hidden;margin-bottom:24px}
              th{background:#2d3748;color:#a0aec0;font-size:.8rem;text-transform:uppercase;letter-spacing:.05em;padding:10px 14px;text-align:left}
              td{padding:10px 14px;border-top:1px solid #2d3748;font-size:.9rem}
              h2{font-size:1rem;font-weight:600;color:#a0aec0;margin-bottom:10px}
              .hist-row{display:flex;align-items:center;gap:8px;margin-bottom:6px;font-size:.82rem}
              .hist-label{width:90px;color:#a0aec0;text-align:right;flex-shrink:0}
              .hist-bar-wrap{flex:1;background:#2d3748;border-radius:3px;height:18px;overflow:hidden}
              .hist-bar{height:100%;background:#4299e1;border-radius:3px;min-width:2px}
              .hist-count{width:60px;text-align:right;color:#718096}
              .tps-row{display:flex;align-items:flex-end;gap:2px;height:80px;margin-bottom:4px}
              .tps-col{flex:1;background:#4299e1;border-radius:2px 2px 0 0;min-height:2px;position:relative}
              .tps-col:hover::after{content:attr(data-v);position:absolute;top:-22px;left:50%;transform:translateX(-50%);background:#2d3748;color:#e2e8f0;padding:2px 6px;border-radius:4px;font-size:.72rem;white-space:nowrap;pointer-events:none}
              .tps-axis{display:flex;justify-content:space-between;font-size:.7rem;color:#718096}
              .section{background:#1a1d27;border:1px solid #2d3748;border-radius:8px;padding:16px;margin-bottom:24px}
            </style>
            </head><body>
            """);

        sb.append("<h1>SE-Tester Load Report</h1>\n");
        sb.append(String.format("<div class=\"subtitle\">Started: %s &nbsp;|&nbsp; Duration: %ds &nbsp;|&nbsp; Target: %s</div>%n",
            startTs, r.getDurationMs() / 1000, "N/A"));

        // ── summary cards
        sb.append("<div class=\"grid\">\n");
        addCard(sb, "Total Sent",    String.valueOf(r.getTotalSent()),    "info");
        addCard(sb, "Success",       String.valueOf(r.getTotalSuccess()), "ok");
        addCard(sb, "Error",         String.valueOf(r.getTotalError()),   r.getTotalError()   > 0 ? "err"  : "ok");
        addCard(sb, "Timeout",       String.valueOf(r.getTotalTimeout()), r.getTotalTimeout() > 0 ? "warn" : "ok");
        addCard(sb, "Success Rate",  String.format("%.2f%%", r.getSuccessRate()),
                                     r.getSuccessRate() >= 99.0 ? "ok" : r.getSuccessRate() >= 95.0 ? "warn" : "err");
        addCard(sb, "Achieved TPS",  String.format("%.1f", r.getAchievedTps()), "info");
        sb.append("</div>\n");

        // ── latency table
        sb.append("<h2>Latency Statistics (ms)</h2>\n");
        sb.append("<table><tr>");
        for (String h : new String[]{"Min","Avg","P50","P90","P95","P99","Max"})
            sb.append("<th>").append(h).append("</th>");
        sb.append("</tr><tr>");
        for (long v : new long[]{r.getLatencyMin(), r.getLatencyAvg(), r.getLatencyP50(),
                                  r.getLatencyP90(), r.getLatencyP95(), r.getLatencyP99(), r.getLatencyMax()})
            sb.append("<td>").append(v).append("</td>");
        sb.append("</tr></table>\n");

        // ── latency histogram
        sb.append("<div class=\"section\">\n<h2>Latency Distribution</h2>\n");
        Map<String,Long> hist = buildHistogram(r);
        long histMax = hist.values().stream().mapToLong(Long::longValue).max().orElse(1);
        for (Map.Entry<String,Long> e : hist.entrySet()) {
            long count = e.getValue();
            int barPct = histMax == 0 ? 0 : (int)(count * 100 / histMax);
            sb.append(String.format(
                "<div class=\"hist-row\"><div class=\"hist-label\">%s</div>" +
                "<div class=\"hist-bar-wrap\"><div class=\"hist-bar\" style=\"width:%d%%\"></div></div>" +
                "<div class=\"hist-count\">%d</div></div>%n",
                e.getKey(), barPct, count));
        }
        sb.append("</div>\n");

        // ── TPS over time
        Map<Long, long[]> tpsMap = buildTpsTimeline(r);
        if (!tpsMap.isEmpty()) {
            sb.append("<div class=\"section\">\n<h2>TPS Over Time (1s buckets)</h2>\n");
            long[] tpsValues = tpsMap.values().stream().mapToLong(a -> a[0]).toArray();
            long tpsMax = Arrays.max(tpsValues);
            if (tpsMax == 0) tpsMax = 1;
            sb.append("<div class=\"tps-row\">\n");
            int idx = 0;
            for (long[] v : tpsMap.values()) {
                long sent = v[0];
                int h = (int)(sent * 100 / tpsMax);
                sb.append(String.format(
                    "<div class=\"tps-col\" style=\"height:%d%%\" data-v=\"%d\"></div>%n",
                    Math.max(h, 1), sent));
                if (++idx > 300) break; // cap columns
            }
            sb.append("</div>\n");
            long firstSec = tpsMap.keySet().iterator().next();
            long lastSec  = tpsMap.keySet().stream().mapToLong(Long::longValue).max().orElse(firstSec);
            sb.append(String.format("<div class=\"tps-axis\"><span>0s</span><span>%ds</span></div>%n",
                lastSec - firstSec + 1));
            sb.append("</div>\n");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static void addCard(StringBuilder sb, String label, String value, String cls) {
        sb.append(String.format(
            "<div class=\"card\"><div class=\"label\">%s</div><div class=\"value %s\">%s</div></div>%n",
            label, cls, value));
    }

    private static Map<String, Long> buildHistogram(LoadResult r) {
        Map<String, Long> h = new LinkedHashMap<>();
        h.put("< 5 ms",    0L);
        h.put("5-10 ms",   0L);
        h.put("10-20 ms",  0L);
        h.put("20-50 ms",  0L);
        h.put("50-100 ms", 0L);
        h.put("100-200 ms",0L);
        h.put("200-500 ms",0L);
        h.put("> 500 ms",  0L);
        for (TransactionRecord rec : r.getRecords()) {
            if (!"SUCCESS".equals(rec.getStatus())) continue;
            long ms = rec.getLatencyMs();
            String bucket;
            if      (ms <   5) bucket = "< 5 ms";
            else if (ms <  10) bucket = "5-10 ms";
            else if (ms <  20) bucket = "10-20 ms";
            else if (ms <  50) bucket = "20-50 ms";
            else if (ms < 100) bucket = "50-100 ms";
            else if (ms < 200) bucket = "100-200 ms";
            else if (ms < 500) bucket = "200-500 ms";
            else               bucket = "> 500 ms";
            h.merge(bucket, 1L, Long::sum);
        }
        return h;
    }

    private static Map<Long, long[]> buildTpsTimeline(LoadResult r) {
        Map<Long, long[]> m = new java.util.TreeMap<>();
        long origin = r.getStartTimeMs() / 1000;
        for (TransactionRecord rec : r.getRecords()) {
            long sec = rec.getTimestampMs() / 1000 - origin;
            m.computeIfAbsent(sec, k -> new long[]{0})[0]++;
        }
        return m;
    }

    // helper used in HTML template string context
    private static class Arrays {
        static long max(long[] a) {
            long m = Long.MIN_VALUE;
            for (long v : a) if (v > m) m = v;
            return m;
        }
    }
}
