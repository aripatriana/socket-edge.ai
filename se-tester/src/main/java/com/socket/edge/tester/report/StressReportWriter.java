package com.socket.edge.tester.report;

import com.socket.edge.tester.model.StressResult;
import com.socket.edge.tester.model.StressStepResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class StressReportWriter {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void writeHtml(StressResult result, Path path, int stepDurationSec) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println(buildHtml(result, stepDurationSec));
        }
    }

    private static String buildHtml(StressResult r, int stepSec) {
        StringBuilder sb = new StringBuilder();
        String startTs = TS_FMT.format(Instant.ofEpochMilli(r.getStartTimeMs()));

        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>SE-Tester Stress Report</title>
            <style>
              *{box-sizing:border-box;margin:0;padding:0}
              body{font-family:'Segoe UI',Arial,sans-serif;background:#0f1117;color:#e2e8f0;padding:24px}
              h1{font-size:1.5rem;font-weight:700;color:#63b3ed;margin-bottom:4px}
              .subtitle{font-size:.85rem;color:#718096;margin-bottom:24px}
              .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin-bottom:24px}
              .card{background:#1a1d27;border:1px solid #2d3748;border-radius:8px;padding:16px}
              .card .label{font-size:.75rem;color:#718096;text-transform:uppercase;letter-spacing:.05em}
              .card .value{font-size:1.6rem;font-weight:700;margin-top:4px}
              .ok{color:#68d391}.warn{color:#f6ad55}.err{color:#fc8181}.info{color:#63b3ed}
              table{width:100%;border-collapse:collapse;background:#1a1d27;border:1px solid #2d3748;border-radius:8px;overflow:hidden;margin-bottom:24px;font-size:.85rem}
              th{background:#2d3748;color:#a0aec0;font-size:.75rem;text-transform:uppercase;letter-spacing:.05em;padding:10px 12px;text-align:right}
              th:first-child,th:nth-child(2){text-align:left}
              td{padding:9px 12px;border-top:1px solid #2d3748;text-align:right}
              td:first-child,td:nth-child(2){text-align:left}
              tr.break-row{background:#2d1515}
              tr.break-row td{color:#fc8181}
              .badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:.75rem;font-weight:600}
              .badge-ok{background:#1a3a2a;color:#68d391}
              .badge-err{background:#3a1a1a;color:#fc8181}
              .chart-wrap{background:#1a1d27;border:1px solid #2d3748;border-radius:8px;padding:16px;margin-bottom:24px}
              h2{font-size:1rem;font-weight:600;color:#a0aec0;margin-bottom:12px}
              .bar-group{display:flex;align-items:flex-end;gap:4px;height:120px;margin-bottom:6px}
              .bar{flex:1;border-radius:3px 3px 0 0;min-height:2px;position:relative;cursor:default}
              .bar:hover::after{content:attr(data-v);position:absolute;top:-26px;left:50%;transform:translateX(-50%);background:#2d3748;color:#e2e8f0;padding:3px 8px;border-radius:4px;font-size:.72rem;white-space:nowrap;z-index:10}
              .bar-ok{background:#4299e1}
              .bar-break{background:#fc8181}
              .bar-axis{display:flex;justify-content:space-between;font-size:.7rem;color:#718096;margin-top:2px}
              .latency-bars{display:flex;align-items:flex-end;gap:3px;height:100px;margin-bottom:4px}
              .latency-bar{flex:1;border-radius:2px 2px 0 0;min-height:2px;cursor:default}
              .latency-bar:hover::after{content:attr(data-v);position:absolute;top:-24px;left:50%;transform:translateX(-50%);background:#2d3748;color:#e2e8f0;padding:2px 6px;border-radius:4px;font-size:.72rem;white-space:nowrap;z-index:10;pointer-events:none}
              .latency-bar{position:relative}
            </style>
            </head><body>
            """);

        sb.append("<h1>SE-Tester Stress Report</h1>\n");
        sb.append(String.format("<div class=\"subtitle\">Started: %s &nbsp;|&nbsp; Step duration: %ds per level</div>%n",
            startTs, stepSec));

        // ── summary cards
        sb.append("<div class=\"grid\">\n");
        addCard(sb, "Max Sustainable TPS", String.valueOf(r.getMaxSustainableTps()), "ok");
        addCard(sb, "Steps Completed", String.valueOf(r.getSteps().size()), "info");
        if (r.isBreakingPointFound()) {
            int breakTps = r.getSteps().stream()
                .filter(StressStepResult::isBreakingPoint)
                .mapToInt(StressStepResult::getTargetTps).findFirst().orElse(0);
            addCard(sb, "Breaking Point", breakTps + " TPS", "err");
        } else {
            addCard(sb, "Breaking Point", "Not reached", "ok");
        }
        // best p95 (at max sustainable)
        r.getSteps().stream()
            .filter(s -> !s.isBreakingPoint())
            .reduce((a, b) -> b)
            .ifPresent(last -> addCard(sb, "P95 at Peak", last.getLatencyP95() + " ms", "info"));
        sb.append("</div>\n");

        // ── TPS achieved chart
        sb.append("<div class=\"chart-wrap\">\n<h2>Achieved TPS per Step</h2>\n");
        sb.append("<div class=\"bar-group\">\n");
        long maxSent = r.getSteps().stream().mapToLong(StressStepResult::getTotalSent).max().orElse(1);
        for (StressStepResult s : r.getSteps()) {
            double achTps = stepSec > 0 ? (double) s.getTotalSent() / stepSec : 0;
            int pct = maxSent == 0 ? 0 : (int)(s.getTotalSent() * 100 / maxSent);
            String cls = s.isBreakingPoint() ? "bar bar-break" : "bar bar-ok";
            sb.append(String.format(
                "<div class=\"%s\" style=\"height:%d%%\" data-v=\"%.1f tps\"></div>%n",
                cls, Math.max(pct, 1), achTps));
        }
        sb.append("</div>\n");
        if (!r.getSteps().isEmpty()) {
            sb.append(String.format(
                "<div class=\"bar-axis\"><span>Step 1 (%d TPS)</span><span>Step %d (%d TPS)</span></div>%n",
                r.getSteps().get(0).getTargetTps(),
                r.getSteps().size(),
                r.getSteps().get(r.getSteps().size() - 1).getTargetTps()));
        }
        sb.append("</div>\n");

        // ── P95 latency chart
        sb.append("<div class=\"chart-wrap\">\n<h2>P95 Latency per Step (ms)</h2>\n");
        sb.append("<div class=\"latency-bars\">\n");
        long maxP95 = r.getSteps().stream().mapToLong(StressStepResult::getLatencyP95).max().orElse(1);
        if (maxP95 == 0) maxP95 = 1;
        for (StressStepResult s : r.getSteps()) {
            int pct = (int)(s.getLatencyP95() * 100 / maxP95);
            String bg = s.isBreakingPoint() ? "#fc8181"
                : s.getLatencyP95() > 200 ? "#f6ad55" : "#4299e1";
            sb.append(String.format(
                "<div class=\"latency-bar\" style=\"height:%d%%;background:%s\" data-v=\"%dms\"></div>%n",
                Math.max(pct, 1), bg, s.getLatencyP95()));
        }
        sb.append("</div>\n</div>\n");

        // ── step table
        sb.append("<h2>Step Details</h2>\n");
        sb.append("<table><tr>");
        for (String h : new String[]{"Step","Target TPS","Sent","OK","Err","Tmo",
                                      "Ach. TPS","P50","P90","P95","P99","Err Rate","Status"})
            sb.append("<th>").append(h).append("</th>");
        sb.append("</tr>\n");
        for (StressStepResult s : r.getSteps()) {
            double achTps = stepSec > 0 ? (double) s.getTotalSent() / stepSec : 0;
            String rowCls = s.isBreakingPoint() ? " class=\"break-row\"" : "";
            String badge  = s.isBreakingPoint()
                ? "<span class=\"badge badge-err\">BREAK</span>"
                : "<span class=\"badge badge-ok\">OK</span>";
            sb.append(String.format(
                "<tr%s><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td>" +
                "<td>%.1f</td><td>%d</td><td>%d</td><td>%d</td><td>%d</td>" +
                "<td>%.2f%%</td><td>%s</td></tr>%n",
                rowCls, s.getStepNum(), s.getTargetTps(),
                s.getTotalSent(), s.getTotalSuccess(), s.getTotalError(), s.getTotalTimeout(),
                achTps,
                s.getLatencyP50(), s.getLatencyP90(), s.getLatencyP95(), s.getLatencyP99(),
                s.getErrorRate(), badge));
        }
        sb.append("</table>\n");

        sb.append("</body></html>");
        return sb.toString();
    }

    private static void addCard(StringBuilder sb, String label, String value, String cls) {
        sb.append(String.format(
            "<div class=\"card\"><div class=\"label\">%s</div><div class=\"value %s\">%s</div></div>%n",
            label, cls, value));
    }
}
