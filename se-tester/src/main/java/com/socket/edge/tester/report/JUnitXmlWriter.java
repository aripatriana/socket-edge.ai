package com.socket.edge.tester.report;

import com.socket.edge.tester.model.*;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes JUnit XML (Surefire / Ant format) so CI systems (GitHub Actions, Jenkins, GitLab CI)
 * can parse results natively.
 *
 * Each TestCase maps to one <testsuite>; each TestStep maps to one <testcase>.
 */
public class JUnitXmlWriter {

    public void write(List<TestResult> results, Path outputPath) throws IOException {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuites>\n");
        for (TestResult r : results) {
            if (r.isDataDriven()) {
                for (TestResult row : r.getDataRows()) appendSuite(xml, row);
            } else {
                appendSuite(xml, r);
            }
        }
        xml.append("</testsuites>\n");
        Files.writeString(outputPath, xml.toString(), StandardCharsets.UTF_8);
    }

    public void writeCollection(CollectionResult collection, Path outputPath) throws IOException {
        StringBuilder xml = new StringBuilder(16384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append(String.format("<testsuites name=\"%s\">\n", esc(collection.getCollectionName())));
        for (SuiteResult suite : collection.getSuites()) {
            for (TestResult r : suite.getResults()) {
                if (r.isDataDriven()) {
                    for (TestResult row : r.getDataRows()) appendSuite(xml, row);
                } else {
                    appendSuite(xml, r);
                }
            }
        }
        xml.append("</testsuites>\n");
        Files.writeString(outputPath, xml.toString(), StandardCharsets.UTF_8);
    }

    public void writeSuite(SuiteResult suite, Path outputPath) throws IOException {
        StringBuilder xml = new StringBuilder(8192);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append(String.format("<testsuites name=\"%s\">\n", esc(suite.getSuiteName())));
        for (TestResult r : suite.getResults()) {
            if (r.isDataDriven()) {
                for (TestResult row : r.getDataRows()) appendSuite(xml, row);
            } else {
                appendSuite(xml, r);
            }
        }
        xml.append("</testsuites>\n");
        Files.writeString(outputPath, xml.toString(), StandardCharsets.UTF_8);
    }

    private void appendSuite(StringBuilder xml, TestResult r) {
        if (r.getStatus() == TestResult.Status.SKIPPED) {
            xml.append(String.format(
                    "  <testsuite name=\"%s\" tests=\"1\" skipped=\"1\" failures=\"0\" errors=\"0\" time=\"0.000\">\n",
                    esc(r.getTestCaseName())));
            xml.append(String.format(
                    "    <testcase name=\"%s\" classname=\"%s\" time=\"0.000\"><skipped/></testcase>\n",
                    esc(r.getTestCaseName()), esc(r.getTestCaseName())));
            xml.append("  </testsuite>\n");
            return;
        }

        int tests    = r.getTotalSteps();
        long failures = r.getFailedSteps();
        int errors   = r.getStatus() == TestResult.Status.ERROR ? 1 : 0;
        double time  = r.getDurationMs() / 1000.0;

        xml.append(String.format(
                "  <testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"%d\" time=\"%.3f\">\n",
                esc(r.getTestCaseName()), tests, failures, errors, time));

        if (r.getStatus() == TestResult.Status.ERROR && r.getSteps().isEmpty()) {
            xml.append(String.format(
                    "    <testcase name=\"%s\" classname=\"%s\" time=\"0.000\">\n",
                    esc(r.getTestCaseName()), esc(r.getTestCaseName())));
            xml.append(String.format(
                    "      <error message=\"%s\" type=\"SetupError\">%s</error>\n",
                    esc(r.getError()), esc(r.getError())));
            xml.append("    </testcase>\n");
        } else {
            for (StepResult step : r.getSteps()) appendTestCase(xml, r.getTestCaseName(), step);
        }

        xml.append("  </testsuite>\n");
    }

    private void appendTestCase(StringBuilder xml, String suiteName, StepResult step) {
        double time = step.getLatencyMs() / 1000.0;
        xml.append(String.format(
                "    <testcase name=\"%s\" classname=\"%s\" time=\"%.3f\">\n",
                esc(step.getStepName()), esc(suiteName), time));

        switch (step.getStatus()) {
            case FAILED -> {
                String message = firstHardFailMessage(step);
                xml.append(String.format(
                        "      <failure message=\"%s\" type=\"AssertionError\">%s</failure>\n",
                        esc(message), esc(message)));
            }
            case ERROR -> {
                String err = step.getError() != null ? step.getError() : "Unknown error";
                xml.append(String.format(
                        "      <error message=\"%s\" type=\"Exception\">%s</error>\n",
                        esc(err), esc(err)));
            }
            case SKIPPED -> xml.append("      <skipped/>\n");
            default -> {} // PASSED — no child element needed
        }

        xml.append("    </testcase>\n");
    }

    private String firstHardFailMessage(StepResult step) {
        if (step.getAssertions() == null) return "Step failed";
        return step.getAssertions().stream()
                .filter(a -> !a.isPassed() && a.getSeverity() == Assertion.Severity.HARD)
                .map(a -> {
                    String desc = a.getMessage() != null ? a.getMessage() : a.getDescription();
                    String actual   = a.getActualValue()   != null ? " actual="   + a.getActualValue()   : "";
                    String expected = a.getExpectedValue() != null ? " expected=" + a.getExpectedValue() : "";
                    return desc + expected + actual;
                })
                .findFirst()
                .orElse("Step failed");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;")
                .replace("'",  "&apos;");
    }
}