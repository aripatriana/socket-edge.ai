package com.socket.edge.tester.report;

import com.socket.edge.tester.core.iso.IsoMessage;
import com.socket.edge.tester.model.*;

import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class HtmlReportGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final double CIRC = 2 * Math.PI * 38; // SVG donut circumference (r=38)

    public void writeSuite(SuiteResult suite, Path output) throws IOException {
        Files.writeString(output, buildHtml(suite, LocalDateTime.now().format(FMT)), StandardCharsets.UTF_8);
    }

    public void writeCollection(CollectionResult collection, Path output) throws IOException {
        Files.writeString(output, buildCollectionHtml(collection, LocalDateTime.now().format(FMT)),
                StandardCharsets.UTF_8);
    }

    public void writeTestCase(TestResult result, Path output) throws IOException {
        SuiteResult wrapper = new SuiteResult(result.getTestCaseName());
        wrapper.getResults().add(result);
        wrapper.setDurationMs(result.getDurationMs());
        wrapper.setAvgLatencyMs(result.getAvgLatencyMs());
        wrapper.setP95LatencyMs(result.getP95LatencyMs());
        wrapper.setMaxLatencyMs(result.getMaxLatencyMs());
        boolean anyBad = result.getStatus() == TestResult.Status.FAILED
                      || result.getStatus() == TestResult.Status.ERROR;
        wrapper.setStatus(anyBad ? SuiteResult.Status.FAILED : SuiteResult.Status.PASSED);
        writeSuite(wrapper, output);
    }

    // =========================================================================
    // Collection document
    // =========================================================================

    private String buildCollectionHtml(CollectionResult col, String generatedAt) {
        boolean ok       = col.isPassed();
        long    total    = col.getTotalCases();
        long    passed   = col.getPassedCases();
        long    rate     = total == 0 ? 0 : Math.round(100.0 * passed / total);

        StringBuilder sb = new StringBuilder(131072);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("<title>SE Tester &mdash; ").append(esc(col.getCollectionName())).append("</title>\n")
          .append("<style>").append(CSS).append("</style>\n")
          .append("</head>\n<body>\n");

        // Topbar
        sb.append("<header class=\"topbar\">\n")
          .append("  <span class=\"topbar-logo\">&#9670; SE Tester</span>\n")
          .append("  <span class=\"topbar-suite\">").append(esc(col.getCollectionName())).append("</span>\n")
          .append("  <span class=\"topbar-meta\">").append(generatedAt).append("</span>\n")
          .append("</header>\n<div class=\"wrap\">\n");

        // Hero
        sb.append("<div class=\"hero\">\n")
          .append("  <div class=\"hero-status ").append(ok ? "hero-pass" : "hero-fail").append("\">\n")
          .append("    <span class=\"hero-icon\">").append(ok ? "&#10003;" : "&#10007;").append("</span>\n")
          .append("    <span class=\"hero-label\">").append(ok ? "ALL SUITES PASSED" : "SUITES FAILED").append("</span>\n")
          .append("  </div>\n")
          .append("  <div class=\"hero-meta\">Duration: <b>").append(fmt(col.getDurationMs())).append("ms</b>")
          .append(" &nbsp;&middot;&nbsp; ").append(col.getTotalSuites()).append(" suites")
          .append(" &nbsp;&middot;&nbsp; ").append(total).append(" cases")
          .append("</div>\n</div>\n");

        // Summary row (donut on total cases, stats show both suites and cases)
        sb.append("<div class=\"summary-row\">\n  <div class=\"donut-card\">\n");
        appendDonut(sb, (int) total, passed, col.getFailedCases(), col.getErrorCases());
        sb.append("    <div class=\"donut-rate\">").append(rate).append("<span class=\"donut-pct\">%</span></div>\n")
          .append("    <div class=\"donut-caption\">Case Success Rate</div>\n")
          .append("  </div>\n");

        sb.append("  <div class=\"stats-card\">\n    <div class=\"stat-grid\">\n");
        statCell(sb, String.valueOf(col.getTotalSuites()),  "Suites",   "stat-neutral");
        statCell(sb, String.valueOf(col.getPassedSuites()), "S.Passed", "stat-pass");
        statCell(sb, String.valueOf(col.getFailedSuites()), "S.Failed", "stat-fail");
        statCell(sb, String.valueOf(total),   "Cases",   "stat-neutral");
        statCell(sb, String.valueOf(passed),  "C.Passed","stat-pass");
        sb.append("    </div>\n");

        sb.append("    <div class=\"progress-wrap\">\n")
          .append("      <div class=\"progress-label\"><b>Case Pass Rate</b> &nbsp;")
          .append(passed).append(" / ").append(total).append("</div>\n")
          .append("      <div class=\"progress-bar\"><div class=\"progress-fill\" style=\"width:").append(rate).append("%\"></div></div>\n")
          .append("    </div>\n");

        if (col.getAvgLatencyMs() > 0) {
            sb.append("    <div class=\"lat-row\">\n");
            latChip(sb, "AVG", col.getAvgLatencyMs());
            latChip(sb, "P95", col.getP95LatencyMs());
            latChip(sb, "MAX", col.getMaxLatencyMs());
            sb.append("    </div>\n");
        }
        sb.append("  </div>\n</div>\n");

        // Suite sections
        sb.append("<div class=\"section-header\">\n")
          .append("  <span class=\"section-title\">Suites</span>\n")
          .append("  <span class=\"section-count\">").append(col.getTotalSuites()).append(" suites &nbsp;&middot;&nbsp; ")
          .append(total).append(" cases</span>\n")
          .append("</div>\n");

        sb.append("<div class=\"tc-list\">\n");
        for (int i = 0; i < col.getSuites().size(); i++) {
            appendSuiteSection(sb, col.getSuites().get(i), i);
        }
        sb.append("</div>\n");

        sb.append("<footer class=\"footer\">SE Tester v1.0 &nbsp;&middot;&nbsp; ").append(generatedAt).append("</footer>\n");
        sb.append("</div>\n<script>").append(JS).append("</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    private void appendSuiteSection(StringBuilder sb, SuiteResult suite, int idx) {
        String accentCls = suite.isPassed() ? "tc-accent-pass" : "tc-accent-fail";
        String badgeCls  = suite.isPassed() ? "badge-pass" : "badge-fail";
        String label     = suite.isPassed() ? "PASS" : "FAIL";
        long   rate      = suite.getTotalCases() == 0 ? 0
                : Math.round(100.0 * suite.getPassedCases() / suite.getTotalCases());
        String suiteBodyId = "suite-body-" + idx;
        String suiteChevId = "suite-chev-" + idx;

        sb.append("<div class=\"tc ").append(accentCls).append("\">\n")
          .append("  <div class=\"tc-header suite-header\" onclick=\"toggle(").append(idx).append(")\">\n")
          .append("    <span class=\"badge ").append(badgeCls).append("\">").append(label).append("</span>\n")
          .append("    <span class=\"tc-name\">").append(esc(suite.getSuiteName())).append("</span>\n")
          .append("    <div class=\"tc-chips\">\n")
          .append("      <span class=\"chip chip-neutral\">").append(suite.getPassedCases()).append("/").append(suite.getTotalCases()).append(" cases</span>\n");
        if (suite.getDurationMs() > 0)
            sb.append("      <span class=\"chip chip-neutral\">").append(fmt(suite.getDurationMs())).append("ms</span>\n");
        if (suite.getAvgLatencyMs() > 0)
            sb.append("      <span class=\"chip ").append(latClass(suite.getAvgLatencyMs())).append("\">avg ")
              .append(suite.getAvgLatencyMs()).append("ms</span>\n");
        sb.append("    </div>\n")
          .append("    <span class=\"chevron\" id=\"chev-").append(idx).append("\">&#8250;</span>\n")
          .append("  </div>\n");

        sb.append("  <div class=\"tc-body suite-body\" id=\"tc-body-").append(idx).append("\">\n");
        if (suite.getError() != null) {
            sb.append("    <div class=\"note-error\">").append(esc(suite.getError())).append("</div>\n");
        } else {
            // nested section header for TCs
            sb.append("    <div class=\"suite-tc-header\">Test Cases &nbsp;<span class=\"chip chip-neutral\">")
              .append(suite.getTotalCases()).append("</span></div>\n");
            for (int t = 0; t < suite.getResults().size(); t++) {
                // offset idx to avoid id collisions: 10000 * suite_idx + tc_idx
                appendTestCase(sb, suite.getResults().get(t), 10000 * (idx + 1) + t);
            }
        }
        sb.append("  </div>\n</div>\n");
    }

    // =========================================================================
    // Document
    // =========================================================================

    private String buildHtml(SuiteResult suite, String generatedAt) {
        int    total   = suite.getTotalCases();
        long   passed  = suite.getPassedCases();
        long   failed  = suite.getFailedCases();
        long   errors  = suite.getErrorCases();
        long   skipped = suite.getSkippedCases();
        long   rate    = total == 0 ? 0 : Math.round(100.0 * passed / total);
        boolean ok     = suite.isPassed();

        StringBuilder sb = new StringBuilder(65536);

        // ── <head> ────────────────────────────────────────────────────────────
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
          .append("<title>SE Tester &mdash; ").append(esc(suite.getSuiteName())).append("</title>\n")
          .append("<style>").append(CSS).append("</style>\n")
          .append("</head>\n<body>\n");

        // ── Sticky header ────────────────────────────────────────────────────
        sb.append("<header class=\"topbar\">\n")
          .append("  <span class=\"topbar-logo\">&#9670; SE Tester</span>\n")
          .append("  <span class=\"topbar-suite\">").append(esc(suite.getSuiteName())).append("</span>\n")
          .append("  <span class=\"topbar-meta\">").append(generatedAt).append("</span>\n")
          .append("</header>\n");

        // ── Main wrapper ─────────────────────────────────────────────────────
        sb.append("<div class=\"wrap\">\n");

        // ── Hero ─────────────────────────────────────────────────────────────
        sb.append("<div class=\"hero\">\n")
          .append("  <div class=\"hero-status ").append(ok ? "hero-pass" : "hero-fail").append("\">\n")
          .append("    <span class=\"hero-icon\">").append(ok ? "&#10003;" : "&#10007;").append("</span>\n")
          .append("    <span class=\"hero-label\">").append(ok ? "ALL TESTS PASSED" : "TESTS FAILED").append("</span>\n")
          .append("  </div>\n")
          .append("  <div class=\"hero-meta\">Duration: <b>").append(fmt(suite.getDurationMs())).append("ms</b>")
          .append(" &nbsp;&middot;&nbsp; ").append(total).append(" test case").append(total != 1 ? "s" : "")
          .append("</div>\n")
          .append("</div>\n");

        // ── Summary row ───────────────────────────────────────────────────────
        sb.append("<div class=\"summary-row\">\n");

        // Donut
        sb.append("  <div class=\"donut-card\">\n");
        appendDonut(sb, total, passed, failed, errors);
        sb.append("    <div class=\"donut-rate\">").append(rate).append("<span class=\"donut-pct\">%</span></div>\n")
          .append("    <div class=\"donut-caption\">Success Rate</div>\n")
          .append("  </div>\n");

        // Stats
        sb.append("  <div class=\"stats-card\">\n")
          .append("    <div class=\"stat-grid\">\n");
        statCell(sb, String.valueOf(total),   "Total",   "stat-neutral");
        statCell(sb, String.valueOf(passed),  "Passed",  "stat-pass");
        statCell(sb, String.valueOf(failed),  "Failed",  "stat-fail");
        statCell(sb, String.valueOf(errors),  "Error",   "stat-error");
        statCell(sb, String.valueOf(skipped), "Skipped", "stat-skip");
        sb.append("    </div>\n");

        // Progress bar
        sb.append("    <div class=\"progress-wrap\">\n")
          .append("      <div class=\"progress-label\"><b>Pass Rate</b> &nbsp;")
          .append(passed).append(" / ").append(total).append("</div>\n")
          .append("      <div class=\"progress-bar\"><div class=\"progress-fill\" style=\"width:").append(rate).append("%\"></div></div>\n")
          .append("    </div>\n");

        // Latency
        if (suite.getAvgLatencyMs() > 0) {
            sb.append("    <div class=\"lat-row\">\n");
            latChip(sb, "AVG", suite.getAvgLatencyMs());
            latChip(sb, "P95", suite.getP95LatencyMs());
            latChip(sb, "MAX", suite.getMaxLatencyMs());
            sb.append("    </div>\n");
        }
        sb.append("  </div>\n"); // stats-card
        sb.append("</div>\n");   // summary-row

        // ── Results section ───────────────────────────────────────────────────
        sb.append("<div class=\"section-header\">\n")
          .append("  <span class=\"section-title\">Test Results</span>\n")
          .append("  <span class=\"section-count\">").append(total).append(" cases</span>\n")
          .append("</div>\n");

        sb.append("<div class=\"tc-list\">\n");
        for (int i = 0; i < suite.getResults().size(); i++) {
            appendTestCase(sb, suite.getResults().get(i), i);
        }
        sb.append("</div>\n");

        // ── Footer ────────────────────────────────────────────────────────────
        sb.append("<footer class=\"footer\">SE Tester v1.0 &nbsp;&middot;&nbsp; ")
          .append(generatedAt).append("</footer>\n");

        sb.append("</div>\n"); // wrap
        sb.append("<script>").append(JS).append("</script>\n");
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    // =========================================================================
    // Test Case
    // =========================================================================

    private void appendTestCase(StringBuilder sb, TestResult tc, int idx) {
        String accentCls = accentClass(tc.getStatus().name());
        String badgeCls  = badgeClass(tc.getStatus().name());
        String label     = statusLabel(tc.getStatus().name());

        long assertPassed = tc.getSteps().stream()
                .flatMap(s -> s.getAssertions() != null ? s.getAssertions().stream() : java.util.stream.Stream.empty())
                .filter(a -> a.getSeverity() != Assertion.Severity.INFO && a.isPassed())
                .count();
        long assertTotal = tc.getSteps().stream()
                .flatMap(s -> s.getAssertions() != null ? s.getAssertions().stream() : java.util.stream.Stream.empty())
                .filter(a -> a.getSeverity() != Assertion.Severity.INFO)
                .count();

        sb.append("<div class=\"tc ").append(accentCls).append("\" id=\"tc-").append(idx).append("\">\n")
          .append("  <div class=\"tc-header\" onclick=\"toggle(").append(idx).append(")\">\n")
          .append("    <span class=\"badge ").append(badgeCls).append("\">").append(label).append("</span>\n")
          .append("    <span class=\"tc-name\">").append(esc(tc.getTestCaseName())).append("</span>\n")
          .append("    <div class=\"tc-chips\">\n");

        if (tc.getDurationMs() > 0)
            sb.append("      <span class=\"chip chip-neutral\">").append(fmt(tc.getDurationMs())).append("ms</span>\n");
        if (tc.getAvgLatencyMs() > 0)
            sb.append("      <span class=\"chip ").append(latClass(tc.getAvgLatencyMs())).append("\">avg ").append(tc.getAvgLatencyMs()).append("ms</span>\n");
        if (assertTotal > 0)
            sb.append("      <span class=\"chip chip-neutral\">").append(assertPassed).append("/").append(assertTotal).append(" asserts</span>\n");

        sb.append("    </div>\n")
          .append("    <span class=\"chevron\" id=\"chev-").append(idx).append("\">&#8250;</span>\n")
          .append("  </div>\n"); // tc-header

        sb.append("  <div class=\"tc-body\" id=\"tc-body-").append(idx).append("\">\n");

        if (tc.getStatus() == TestResult.Status.SKIPPED) {
            sb.append("    <div class=\"note-skip\">Skipped &mdash; stopOnFail triggered by a prior failure</div>\n");
        } else if (tc.getStatus() == TestResult.Status.ERROR && tc.getSteps().isEmpty() && !tc.isDataDriven()) {
            sb.append("    <div class=\"note-error\">").append(esc(tc.getError())).append("</div>\n");
        } else if (tc.isDataDriven()) {
            appendDataRows(sb, tc, idx);
        } else {
            for (int s = 0; s < tc.getSteps().size(); s++) {
                appendStep(sb, tc.getSteps().get(s), idx, s);
            }
        }

        sb.append("  </div>\n") // tc-body
          .append("</div>\n");  // tc
    }

    // =========================================================================
    // Step
    // =========================================================================

    private void appendStep(StringBuilder sb, StepResult step, int tcIdx, int stepIdx) {
        String badgeCls = badgeClass(step.getStatus().name());
        String label    = statusLabel(step.getStatus().name());

        long assertPassed = step.getAssertions() == null ? 0 :
                step.getAssertions().stream().filter(a -> a.getSeverity() != Assertion.Severity.INFO && a.isPassed()).count();
        long assertTotal  = step.getAssertions() == null ? 0 :
                step.getAssertions().stream().filter(a -> a.getSeverity() != Assertion.Severity.INFO).count();

        sb.append("  <div class=\"step\">\n")
          .append("    <div class=\"step-header\">\n")
          .append("      <span class=\"badge ").append(badgeCls).append("\">").append(label).append("</span>\n")
          .append("      <span class=\"step-name\">").append(esc(step.getStepName())).append("</span>\n");

        if (step.getLatencyMs() > 0)
            sb.append("      <span class=\"chip ").append(latClass(step.getLatencyMs())).append("\">")
              .append(step.getLatencyMs()).append("ms</span>\n");
        if (assertTotal > 0)
            sb.append("      <span class=\"chip chip-neutral\">")
              .append(assertPassed).append("/").append(assertTotal).append(" asserts</span>\n");

        sb.append("    </div>\n"); // step-header

        if (step.getError() != null)
            sb.append("    <div class=\"note-error\">").append(esc(step.getError())).append("</div>\n");

        // Request / Response
        if (step.getRequest() != null || step.getResponse() != null) {
            sb.append("    <div class=\"req-res\">\n");
            if (step.getRequest()  != null) appendFields(sb, "Request",  step.getRequest(),  true);
            if (step.getResponse() != null) appendFields(sb, "Response", step.getResponse(), false);
            sb.append("    </div>\n");
        }

        // Assertions
        if (assertTotal > 0) {
            sb.append("    <div class=\"assert-header\">Assertions &nbsp;<span class=\"chip chip-neutral\">")
              .append(assertPassed).append("/").append(assertTotal).append("</span></div>\n")
              .append("    <table class=\"assert-table\">\n")
              .append("      <thead><tr><th>&#8203;</th><th>Assertion</th><th>Expected</th><th>Actual</th><th>Severity</th></tr></thead>\n")
              .append("      <tbody>\n");

            for (AssertionResult ar : step.getAssertions()) {
                if (ar.getSeverity() == Assertion.Severity.INFO) continue;
                boolean arOk = ar.isPassed();
                sb.append("        <tr class=\"").append(arOk ? "ar-ok" : "ar-ng").append("\">\n")
                  .append("          <td class=\"ar-icon\">").append(arOk ? "&#10003;" : "&#10007;").append("</td>\n")
                  .append("          <td>").append(esc(ar.getDescription())).append("</td>\n")
                  .append("          <td class=\"ar-mono\">").append(esc(ar.getExpectedValue())).append("</td>\n")
                  .append("          <td class=\"ar-mono\">").append(esc(ar.getActualValue())).append("</td>\n")
                  .append("          <td><span class=\"sev-").append(ar.getSeverity() != null ? ar.getSeverity().name().toLowerCase() : "hard").append("\">")
                  .append(ar.getSeverity() != null ? ar.getSeverity().name() : "").append("</span></td>\n")
                  .append("        </tr>\n");
                if (!arOk && ar.getMessage() != null) {
                    sb.append("        <tr class=\"ar-msg\"><td colspan=\"5\">")
                      .append(esc(ar.getMessage())).append("</td></tr>\n");
                }
            }
            sb.append("      </tbody>\n    </table>\n");
        }

        // Sub-steps (from CALL keyword)
        if (step.getSubSteps() != null && !step.getSubSteps().isEmpty()) {
            sb.append("    <div class=\"substeps\">\n")
              .append("      <div class=\"substeps-label\">Keyword steps</div>\n");
            for (int i = 0; i < step.getSubSteps().size(); i++) {
                appendSubStep(sb, step.getSubSteps().get(i));
            }
            sb.append("    </div>\n");
        }

        sb.append("  </div>\n"); // step
    }

    // =========================================================================
    // Data-driven rows
    // =========================================================================

    private void appendDataRows(StringBuilder sb, TestResult tc, int tcIdx) {
        List<TestResult> rows = tc.getDataRows();
        long passedRows = rows.stream().filter(TestResult::isPassed).count();

        sb.append("  <div class=\"dr-summary\">")
          .append("<span class=\"chip chip-neutral\">").append(rows.size()).append(" rows</span> &nbsp;")
          .append("<span class=\"chip chip-green\">").append(passedRows).append(" passed</span>");
        if (passedRows < rows.size())
            sb.append(" &nbsp;<span class=\"chip chip-red\">").append(rows.size() - passedRows).append(" failed</span>");
        sb.append("</div>\n");

        sb.append("  <table class=\"dr-table\">\n")
          .append("    <thead><tr><th>#</th><th>Row Data</th><th>Status</th><th>Duration</th><th>Latency</th></tr></thead>\n")
          .append("    <tbody>\n");

        for (int i = 0; i < rows.size(); i++) {
            TestResult row = rows.get(i);
            String rowBadge = badgeClass(row.getStatus().name());
            String rowId    = "dr-" + tcIdx + "-" + i;

            sb.append("      <tr class=\"dr-row\" onclick=\"toggleRow('").append(rowId).append("')\">\n")
              .append("        <td class=\"dr-idx\">").append(row.getDataRowIndex()).append("</td>\n")
              .append("        <td class=\"dr-label\">").append(esc(row.getDataRowLabel())).append("</td>\n")
              .append("        <td><span class=\"badge ").append(rowBadge).append("\">").append(statusLabel(row.getStatus().name())).append("</span></td>\n")
              .append("        <td class=\"dr-dur\">").append(row.getDurationMs()).append("ms</td>\n")
              .append("        <td class=\"dr-lat\">");
            if (row.getAvgLatencyMs() > 0)
                sb.append("<span class=\"chip ").append(latClass(row.getAvgLatencyMs())).append("\">avg ")
                  .append(row.getAvgLatencyMs()).append("ms</span>");
            sb.append("</td>\n      </tr>\n");

            // Expandable detail row
            sb.append("      <tr id=\"").append(rowId).append("\" class=\"dr-detail\" style=\"display:none\">\n")
              .append("        <td colspan=\"5\">\n");
            for (int s = 0; s < row.getSteps().size(); s++) {
                appendStep(sb, row.getSteps().get(s), tcIdx * 1000 + i * 100 + s, s);
            }
            sb.append("        </td>\n      </tr>\n");
        }

        sb.append("    </tbody>\n  </table>\n");
    }

    private void appendSubStep(StringBuilder sb, StepResult sub) {
        String badgeCls = badgeClass(sub.getStatus().name());
        String label    = statusLabel(sub.getStatus().name());

        sb.append("      <div class=\"substep\">\n")
          .append("        <div class=\"substep-header\">\n")
          .append("          <span class=\"badge ").append(badgeCls).append("\">").append(label).append("</span>\n")
          .append("          <span class=\"substep-name\">").append(esc(sub.getStepName())).append("</span>\n");

        if (sub.getLatencyMs() > 0)
            sb.append("          <span class=\"chip ").append(latClass(sub.getLatencyMs())).append("\">")
              .append(sub.getLatencyMs()).append("ms</span>\n");

        sb.append("        </div>\n");

        if (sub.getRequest() != null || sub.getResponse() != null) {
            sb.append("        <div class=\"req-res\">\n");
            if (sub.getRequest()  != null) appendFields(sb, "Request",  sub.getRequest(),  true);
            if (sub.getResponse() != null) appendFields(sb, "Response", sub.getResponse(), false);
            sb.append("        </div>\n");
        }

        if (sub.getAssertions() != null && !sub.getAssertions().isEmpty()) {
            long assertTotal = sub.getAssertions().stream()
                    .filter(a -> a.getSeverity() != Assertion.Severity.INFO).count();
            if (assertTotal > 0) {
                long assertPassed = sub.getAssertions().stream()
                        .filter(a -> a.getSeverity() != Assertion.Severity.INFO && a.isPassed()).count();
                sb.append("        <div class=\"assert-header\">Assertions &nbsp;<span class=\"chip chip-neutral\">")
                  .append(assertPassed).append("/").append(assertTotal).append("</span></div>\n")
                  .append("        <table class=\"assert-table\">\n")
                  .append("          <thead><tr><th>&#8203;</th><th>Assertion</th><th>Expected</th><th>Actual</th><th>Severity</th></tr></thead>\n")
                  .append("          <tbody>\n");
                for (AssertionResult ar : sub.getAssertions()) {
                    if (ar.getSeverity() == Assertion.Severity.INFO) continue;
                    sb.append("            <tr class=\"").append(ar.isPassed() ? "ar-ok" : "ar-ng").append("\">\n")
                      .append("              <td class=\"ar-icon\">").append(ar.isPassed() ? "&#10003;" : "&#10007;").append("</td>\n")
                      .append("              <td>").append(esc(ar.getDescription())).append("</td>\n")
                      .append("              <td class=\"ar-mono\">").append(esc(ar.getExpectedValue())).append("</td>\n")
                      .append("              <td class=\"ar-mono\">").append(esc(ar.getActualValue())).append("</td>\n")
                      .append("              <td><span class=\"sev-").append(ar.getSeverity() != null ? ar.getSeverity().name().toLowerCase() : "hard").append("\">")
                      .append(ar.getSeverity() != null ? ar.getSeverity().name() : "").append("</span></td>\n")
                      .append("            </tr>\n");
                }
                sb.append("          </tbody>\n        </table>\n");
            }
        }

        sb.append("      </div>\n"); // substep
    }

    // =========================================================================
    // Fields
    // =========================================================================

    private void appendFields(StringBuilder sb, String label, IsoMessage msg, boolean isRequest) {
        boolean isResp = !isRequest;
        sb.append("      <div class=\"fields-box\">\n")
          .append("        <div class=\"fields-header\">")
          .append("<span class=\"fields-dir\">").append(isRequest ? "&#8594;" : "&#8592;").append("</span> ")
          .append(label).append(" &nbsp;<span class=\"mti-badge\">MTI: ").append(esc(msg.getMti())).append("</span>")
          .append("</div>\n")
          .append("        <table class=\"fields-table\">\n");

        for (Map.Entry<Integer, String> e : msg.getFields().entrySet()) {
            String val = e.getValue() != null ? e.getValue().trim() : "";
            if (isRequest && e.getKey() == 2) val = maskPan(val);
            String highlight = isResp && (e.getKey() == 39) ? " de-rc" : "";
            sb.append("          <tr><td class=\"de-key\">DE").append(e.getKey())
              .append("</td><td class=\"de-val").append(highlight).append("\">").append(esc(val)).append("</td></tr>\n");
        }
        sb.append("        </table>\n      </div>\n");
    }

    // =========================================================================
    // SVG Donut
    // =========================================================================

    private void appendDonut(StringBuilder sb, int total, long passed, long failed, long errors) {
        sb.append("  <div class=\"donut-wrap\">\n    <svg viewBox=\"0 0 100 100\" class=\"donut-svg\">\n");

        // Background ring
        sb.append("      <circle cx=\"50\" cy=\"50\" r=\"38\" fill=\"none\" stroke=\"#e2e8f0\" stroke-width=\"13\"/>\n");

        if (total > 0) {
            double passedArc = (double) passed / total * CIRC;
            double failedArc = (double) failed / total * CIRC;
            double errorArc  = (double) errors / total * CIRC;

            // Errors arc (amber) — drawn first at top
            if (errorArc > 0) {
                double offset = -(passedArc + failedArc);
                sb.append("      <circle cx=\"50\" cy=\"50\" r=\"38\" fill=\"none\" stroke=\"#f59e0b\" stroke-width=\"13\"")
                  .append(" stroke-dasharray=\"").append(fmt2(errorArc)).append(" ").append(fmt2(CIRC)).append("\"")
                  .append(" stroke-dashoffset=\"").append(fmt2(offset)).append("\"")
                  .append(" transform=\"rotate(-90 50 50)\"/>\n");
            }
            // Failed arc (red)
            if (failedArc > 0) {
                double offset = -(passedArc);
                sb.append("      <circle cx=\"50\" cy=\"50\" r=\"38\" fill=\"none\" stroke=\"#ef4444\" stroke-width=\"13\"")
                  .append(" stroke-dasharray=\"").append(fmt2(failedArc)).append(" ").append(fmt2(CIRC)).append("\"")
                  .append(" stroke-dashoffset=\"").append(fmt2(offset)).append("\"")
                  .append(" transform=\"rotate(-90 50 50)\"/>\n");
            }
            // Passed arc (green) — top of circle
            if (passedArc > 0) {
                sb.append("      <circle cx=\"50\" cy=\"50\" r=\"38\" fill=\"none\" stroke=\"#1E8449\" stroke-width=\"13\"")
                  .append(" stroke-dasharray=\"").append(fmt2(passedArc)).append(" ").append(fmt2(CIRC)).append("\"")
                  .append(" stroke-dashoffset=\"0\"")
                  .append(" transform=\"rotate(-90 50 50)\"/>\n");
            }
        }
        sb.append("    </svg>\n  </div>\n");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void statCell(StringBuilder sb, String val, String label, String cls) {
        sb.append("      <div class=\"stat-cell ").append(cls).append("\">\n")
          .append("        <div class=\"stat-val\">").append(val).append("</div>\n")
          .append("        <div class=\"stat-lbl\">").append(label).append("</div>\n")
          .append("      </div>\n");
    }

    private static void latChip(StringBuilder sb, String label, long ms) {
        sb.append("      <span class=\"lat-chip ").append(latClass(ms)).append("\">")
          .append("<b>").append(label).append("</b> ").append(ms).append("ms</span>\n");
    }

    private static String latClass(long ms) {
        if (ms <= 300)  return "chip-green";
        if (ms <= 1500) return "chip-amber";
        return "chip-red";
    }

    private static String accentClass(String status) {
        return switch (status) {
            case "PASSED"  -> "tc-accent-pass";
            case "FAILED"  -> "tc-accent-fail";
            case "ERROR"   -> "tc-accent-error";
            default        -> "tc-accent-skip";
        };
    }

    private static String badgeClass(String status) {
        return switch (status) {
            case "PASSED"  -> "badge-pass";
            case "FAILED"  -> "badge-fail";
            case "ERROR"   -> "badge-error";
            default        -> "badge-skip";
        };
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "PASSED"  -> "PASS";
            case "FAILED"  -> "FAIL";
            case "ERROR"   -> "ERR";
            case "SKIPPED" -> "SKIP";
            default        -> status;
        };
    }

    private static String maskPan(String v) {
        if (v == null || v.length() < 10) return v;
        return v.substring(0, 6) + "&#9679;&#9679;&#9679;&#9679;&#9679;&#9679;" + v.substring(v.length() - 4);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String fmt(long ms) {
        return String.format("%,d", ms);
    }

    private static String fmt2(double v) {
        return String.format("%.2f", v);
    }

    // =========================================================================
    // CSS
    // =========================================================================

    private static final String CSS =
        ":root{--c-bg:#f1f5f9;--c-surface:#ffffff;--c-border:#e2e8f0;" +
        "--c-text:#0f172a;--c-muted:#64748b;--c-pass:#1E8449;--c-fail:#dc2626;" +
        "--c-error:#d97706;--c-skip:#94a3b8;--c-accent:#1B6EC2;--c-heading:#154360;" +
        "--c-pass-bg:#e8f5e9;--c-fail-bg:#fee2e2;--c-error-bg:#fef3c7;--c-skip-bg:#f1f5f9;" +
        "--shadow:0 1px 3px rgba(0,0,0,.08),0 1px 2px rgba(0,0,0,.06);}" +
        "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0;}" +
        "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;" +
        "font-size:13px;background:var(--c-bg);color:var(--c-text);line-height:1.5;}" +

        // Topbar
        ".topbar{position:sticky;top:0;z-index:100;background:#154360;color:#f8fafc;" +
        "display:flex;align-items:center;gap:12px;padding:0 24px;height:48px;" +
        "box-shadow:0 2px 8px rgba(0,0,0,.25);}" +
        ".topbar-logo{font-weight:700;font-size:15px;color:#90CAF9;letter-spacing:.5px;white-space:nowrap;}" +
        ".topbar-suite{flex:1;font-size:13px;color:#cbd5e1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}" +
        ".topbar-meta{font-size:11px;color:#64748b;white-space:nowrap;}" +

        // Wrap
        ".wrap{max-width:1080px;margin:0 auto;padding:24px 20px 48px;}" +

        // Hero
        ".hero{text-align:center;padding:32px 0 24px;}" +
        ".hero-status{display:inline-flex;align-items:center;gap:10px;padding:10px 28px;" +
        "border-radius:100px;font-size:17px;font-weight:700;letter-spacing:1px;margin-bottom:10px;}" +
        ".hero-pass{background:var(--c-pass-bg);color:var(--c-pass);}" +
        ".hero-fail{background:var(--c-fail-bg);color:var(--c-fail);}" +
        ".hero-icon{font-size:20px;}" +
        ".hero-label{color:var(--c-heading)!important;}" +
        ".hero-meta{font-size:13px;color:var(--c-muted);}" +

        // Summary row
        ".summary-row{display:flex;gap:16px;margin-bottom:24px;flex-wrap:wrap;}" +

        // Donut card
        ".donut-card{background:var(--c-surface);border-radius:12px;box-shadow:var(--shadow);" +
        "border:1px solid var(--c-border);padding:20px 24px;display:flex;flex-direction:column;" +
        "align-items:center;justify-content:center;min-width:160px;}" +
        ".donut-wrap{position:relative;width:100px;height:100px;}" +
        ".donut-svg{width:100px;height:100px;}" +
        ".donut-rate{font-size:28px;font-weight:800;color:var(--c-text);margin-top:8px;line-height:1;}" +
        ".donut-pct{font-size:16px;font-weight:500;color:var(--c-muted);}" +
        ".donut-caption{font-size:11px;color:var(--c-muted);margin-top:2px;text-transform:uppercase;letter-spacing:.5px;}" +

        // Stats card
        ".stats-card{flex:1;background:var(--c-surface);border-radius:12px;box-shadow:var(--shadow);" +
        "border:1px solid var(--c-border);padding:20px 24px;display:flex;flex-direction:column;gap:16px;}" +
        ".stat-grid{display:flex;gap:8px;flex-wrap:wrap;}" +
        ".stat-cell{flex:1;min-width:64px;border-radius:8px;padding:10px 12px;text-align:center;}" +
        ".stat-val{font-size:24px;font-weight:700;line-height:1;}" +
        ".stat-lbl{font-size:10px;color:var(--c-muted);margin-top:3px;text-transform:uppercase;letter-spacing:.4px;}" +
        ".stat-neutral{background:#f8fafc;}" +
        ".stat-pass{background:var(--c-pass-bg);color:var(--c-pass);}" +
        ".stat-fail{background:var(--c-fail-bg);color:var(--c-fail);}" +
        ".stat-error{background:var(--c-error-bg);color:var(--c-error);}" +
        ".stat-skip{background:var(--c-skip-bg);color:var(--c-skip);}" +

        // Progress bar
        ".progress-wrap{}" +
        ".progress-label{font-size:12px;color:var(--c-muted);margin-bottom:5px;}" +
        ".progress-bar{height:8px;background:#e2e8f0;border-radius:99px;overflow:hidden;}" +
        ".progress-fill{height:100%;background:linear-gradient(90deg,#1E8449,#155a32);border-radius:99px;" +
        "transition:width .6s ease;}" +

        // Latency row
        ".lat-row{display:flex;gap:8px;flex-wrap:wrap;}" +
        ".lat-chip{padding:4px 10px;border-radius:6px;font-size:12px;}" +
        ".lat-chip b{font-weight:600;margin-right:4px;}" +

        // Chips (general)
        ".chip{display:inline-block;padding:2px 8px;border-radius:5px;font-size:11px;font-weight:500;}" +
        ".chip-neutral{background:#f1f5f9;color:#475569;}" +
        ".chip-green,.lat-chip.chip-green{background:#e8f5e9;color:#1E8449;}" +
        ".chip-amber,.lat-chip.chip-amber{background:#fef3c7;color:#b45309;}" +
        ".chip-red,.lat-chip.chip-red{background:#fee2e2;color:#b91c1c;}" +

        // Section header
        ".section-header{display:flex;align-items:center;justify-content:space-between;" +
        "padding:0 2px 8px;margin-bottom:8px;border-bottom:2px solid var(--c-border);}" +
        ".section-title{font-size:14px;font-weight:700;color:var(--c-heading);text-transform:uppercase;letter-spacing:.5px;}" +
        ".section-count{font-size:12px;color:var(--c-muted);}" +

        // TC list
        ".tc-list{display:flex;flex-direction:column;gap:6px;}" +
        ".tc{background:var(--c-surface);border-radius:10px;box-shadow:var(--shadow);" +
        "border:1px solid var(--c-border);overflow:hidden;border-left-width:4px;}" +
        ".tc-accent-pass{border-left-color:var(--c-pass);}" +
        ".tc-accent-fail{border-left-color:var(--c-fail);}" +
        ".tc-accent-error{border-left-color:var(--c-error);}" +
        ".tc-accent-skip{border-left-color:var(--c-skip);}" +

        ".tc-header{display:flex;align-items:center;gap:10px;padding:12px 16px;" +
        "cursor:pointer;user-select:none;transition:background .15s;}" +
        ".tc-header:hover{background:#f8fafc;}" +
        ".tc-name{flex:1;font-weight:600;font-size:13px;color:var(--c-heading);}" +
        ".tc-chips{display:flex;gap:5px;flex-wrap:wrap;align-items:center;}" +
        ".chevron{font-size:18px;color:var(--c-muted);transition:transform .25s;line-height:1;}" +
        ".chevron.open{transform:rotate(90deg);}" +

        ".tc-body{overflow:hidden;height:0;transition:height .28s ease;}" +

        // Step
        ".step{border-top:1px solid var(--c-border);padding:14px 16px;}" +
        ".step-header{display:flex;align-items:center;gap:8px;margin-bottom:10px;flex-wrap:wrap;}" +
        ".step-name{flex:1;font-weight:500;font-size:13px;}" +

        // Badges
        ".badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:10px;" +
        "font-weight:700;letter-spacing:.5px;white-space:nowrap;}" +
        ".badge-pass{background:var(--c-pass-bg);color:var(--c-pass);}" +
        ".badge-fail{background:var(--c-fail-bg);color:var(--c-fail);}" +
        ".badge-error{background:var(--c-error-bg);color:var(--c-error);}" +
        ".badge-skip{background:var(--c-skip-bg);color:var(--c-skip);}" +

        // Request / Response
        ".req-res{display:flex;gap:10px;margin:0 0 10px;flex-wrap:wrap;}" +
        ".fields-box{flex:1;min-width:220px;border:1px solid var(--c-border);border-radius:8px;overflow:hidden;}" +
        ".fields-header{background:#f8fafc;padding:7px 12px;font-size:12px;font-weight:600;" +
        "color:var(--c-muted);border-bottom:1px solid var(--c-border);display:flex;align-items:center;gap:6px;}" +
        ".fields-dir{font-size:14px;color:var(--c-accent);}" +
        ".mti-badge{background:#e3f0fb;color:var(--c-accent);padding:1px 6px;border-radius:4px;" +
        "font-weight:700;font-size:11px;margin-left:auto;}" +
        ".fields-table{width:100%;border-collapse:collapse;font-size:12px;}" +
        ".fields-table tr:nth-child(even){background:#fafafa;}" +
        ".fields-table td{padding:3px 10px;border-bottom:1px solid #f0f0f0;}" +
        ".de-key{color:var(--c-muted);width:44px;font-weight:600;font-size:11px;white-space:nowrap;}" +
        ".de-val{font-family:'Cascadia Code','Consolas','Courier New',monospace;font-size:11.5px;}" +
        ".de-rc{color:var(--c-pass);font-weight:700;}" +

        // Assertions
        ".assert-header{font-size:12px;font-weight:600;color:var(--c-muted);margin-bottom:6px;" +
        "text-transform:uppercase;letter-spacing:.4px;display:flex;align-items:center;gap:6px;}" +
        ".assert-table{width:100%;border-collapse:collapse;font-size:12px;border-radius:8px;overflow:hidden;" +
        "border:1px solid var(--c-border);}" +
        ".assert-table thead tr{background:#f8fafc;}" +
        ".assert-table th{padding:7px 10px;text-align:left;font-weight:600;color:var(--c-muted);" +
        "font-size:11px;text-transform:uppercase;letter-spacing:.4px;border-bottom:1px solid var(--c-border);}" +
        ".assert-table td{padding:5px 10px;border-bottom:1px solid #f8f8f8;}" +
        ".ar-icon{width:28px;text-align:center;font-size:14px;}" +
        ".ar-ok .ar-icon{color:var(--c-pass);font-weight:700;}" +
        ".ar-ng .ar-icon{color:var(--c-fail);font-weight:700;}" +
        ".ar-ng{background:#fffafa;}" +
        ".ar-mono{font-family:'Cascadia Code','Consolas','Courier New',monospace;font-size:11.5px;}" +
        ".ar-msg td{color:var(--c-fail);font-style:italic;font-size:11px;background:#fff5f5;padding-left:28px;}" +
        ".sev-hard{color:var(--c-fail);font-weight:600;font-size:10px;text-transform:uppercase;}" +
        ".sev-soft{color:var(--c-error);font-weight:600;font-size:10px;text-transform:uppercase;}" +

        // Suite section (inside collection)
        ".suite-header{background:#f8fafc;}" +
        ".suite-body .tc{margin:8px 12px;border-radius:8px;}" +
        ".suite-tc-header{padding:8px 16px 4px;font-size:11px;font-weight:700;color:var(--c-muted);" +
        "text-transform:uppercase;letter-spacing:.4px;display:flex;align-items:center;gap:6px;" +
        "border-top:1px solid var(--c-border);}" +

        // Data-driven table
        ".dr-summary{padding:10px 16px 6px;display:flex;gap:6px;align-items:center;}" +
        ".dr-table{width:100%;border-collapse:collapse;font-size:12px;}" +
        ".dr-table thead tr{background:#f8fafc;}" +
        ".dr-table th{padding:7px 12px;text-align:left;font-weight:600;color:var(--c-muted);" +
        "font-size:11px;text-transform:uppercase;letter-spacing:.4px;border-bottom:2px solid var(--c-border);}" +
        ".dr-row{cursor:pointer;transition:background .1s;}" +
        ".dr-row:hover{background:#f8fafc;}" +
        ".dr-row td{padding:7px 12px;border-bottom:1px solid #f0f0f0;}" +
        ".dr-idx{color:var(--c-muted);font-weight:600;width:32px;}" +
        ".dr-label{font-family:'Cascadia Code','Consolas','Courier New',monospace;font-size:11.5px;}" +
        ".dr-dur,.dr-lat{white-space:nowrap;}" +
        ".dr-detail td{padding:0;background:#fafcff;border-bottom:1px solid var(--c-border);}" +

        // Sub-steps (CALL keyword)
        ".substeps{margin:8px 0 0;border:1px solid var(--c-border);border-radius:8px;overflow:hidden;background:#fafcff;}" +
        ".substeps-label{padding:6px 12px;font-size:11px;font-weight:700;color:var(--c-accent);" +
        "text-transform:uppercase;letter-spacing:.5px;background:#f0f6ff;border-bottom:1px solid var(--c-border);}" +
        ".substep{border-top:1px solid var(--c-border);padding:10px 14px;}" +
        ".substep:first-of-type{border-top:none;}" +
        ".substep-header{display:flex;align-items:center;gap:8px;margin-bottom:8px;flex-wrap:wrap;}" +
        ".substep-name{flex:1;font-weight:500;font-size:12px;color:var(--c-heading);}" +

        // Notes
        ".note-skip{padding:14px 16px;color:var(--c-skip);font-style:italic;font-size:12px;}" +
        ".note-error{padding:12px 16px;color:var(--c-fail);background:#fff5f5;" +
        "border-left:3px solid var(--c-fail);font-family:monospace;font-size:12px;}" +

        // Footer
        ".footer{text-align:center;padding:32px 0 8px;font-size:11px;color:var(--c-skip);}" +

        // Print
        "@media print{.topbar{position:static;}.tc-body{height:auto!important;}}";

    // =========================================================================
    // JS
    // =========================================================================

    private static final String JS =
        "function toggleRow(id){" +
        "  var r=document.getElementById(id);" +
        "  r.style.display=r.style.display==='none'?'table-row':'none';" +
        "}" +
        "function toggle(idx){" +
        "  var b=document.getElementById('tc-body-'+idx)," +
        "      c=document.getElementById('chev-'+idx);" +
        "  if(b.classList.contains('open')){" +
        "    b.style.height=b.scrollHeight+'px';" +
        "    requestAnimationFrame(function(){b.style.height='0';});" +
        "    b.classList.remove('open');c.classList.remove('open');" +
        "  } else {" +
        "    b.classList.add('open');c.classList.add('open');" +
        "    b.style.height='0';" +
        "    requestAnimationFrame(function(){" +
        "      b.style.height=b.scrollHeight+'px';" +
        "      b.addEventListener('transitionend',function h(){" +
        "        b.style.height='auto';b.removeEventListener('transitionend',h);" +
        "      });" +
        "    });" +
        "  }" +
        "}";
}
