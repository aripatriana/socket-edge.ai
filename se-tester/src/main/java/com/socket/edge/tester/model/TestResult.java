package com.socket.edge.tester.model;

import java.util.ArrayList;
import java.util.List;

public class TestResult {

    public enum Status { PASSED, FAILED, ERROR, SKIPPED }

    private String testCaseName;
    private Status status;
    private long durationMs;
    private List<StepResult> steps = new ArrayList<>();
    private String error; // top-level error (setup/teardown failure)

    // Data-driven fields (populated when TC has dataFile)
    private List<TestResult> dataRows;
    private int dataRowIndex;     // 1-based row number
    private String dataRowLabel;  // short description of CSV row values

    // Computed stats
    private long avgLatencyMs;
    private long p95LatencyMs;
    private long maxLatencyMs;

    public TestResult(String testCaseName) {
        this.testCaseName = testCaseName;
    }

    public boolean isPassed() { return status == Status.PASSED; }

    public int getTotalSteps()   { return steps.size(); }
    public long getPassedSteps() { return steps.stream().filter(s -> s.getStatus() == StepResult.Status.PASSED).count(); }
    public long getFailedSteps() { return steps.stream().filter(s -> s.getStatus() == StepResult.Status.FAILED).count(); }

    public String getTestCaseName() { return testCaseName; }
    public void setTestCaseName(String testCaseName) { this.testCaseName = testCaseName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public List<StepResult> getSteps() { return steps; }
    public void setSteps(List<StepResult> steps) { this.steps = steps; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public boolean isDataDriven() { return dataRows != null && !dataRows.isEmpty(); }

    public List<TestResult> getDataRows() { return dataRows; }
    public void setDataRows(List<TestResult> dataRows) { this.dataRows = dataRows; }

    public int getDataRowIndex() { return dataRowIndex; }
    public void setDataRowIndex(int dataRowIndex) { this.dataRowIndex = dataRowIndex; }

    public String getDataRowLabel() { return dataRowLabel; }
    public void setDataRowLabel(String dataRowLabel) { this.dataRowLabel = dataRowLabel; }

    public long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public long getP95LatencyMs() { return p95LatencyMs; }
    public void setP95LatencyMs(long p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }

    public long getMaxLatencyMs() { return maxLatencyMs; }
    public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }
}