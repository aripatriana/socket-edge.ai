package com.socket.edge.tester.model;

import java.util.ArrayList;
import java.util.List;

public class SuiteResult {

    public enum Status { PASSED, FAILED, ERROR }

    private String suiteName;
    private Status status;
    private long durationMs;
    private List<TestResult> results = new ArrayList<>();

    private String error; // set when suite fails to load/run

    private long avgLatencyMs;
    private long p95LatencyMs;
    private long maxLatencyMs;

    public SuiteResult(String suiteName) {
        this.suiteName = suiteName;
    }

    public boolean isPassed() { return status == Status.PASSED; }

    public int getTotalCases()   { return results.size(); }
    public long getPassedCases() { return results.stream().filter(TestResult::isPassed).count(); }
    public long getFailedCases() { return results.stream().filter(r -> r.getStatus() == TestResult.Status.FAILED).count(); }
    public long getErrorCases()  { return results.stream().filter(r -> r.getStatus() == TestResult.Status.ERROR).count(); }
    public long getSkippedCases(){ return results.stream().filter(r -> r.getStatus() == TestResult.Status.SKIPPED).count(); }

    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = suiteName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public List<TestResult> getResults() { return results; }
    public void setResults(List<TestResult> results) { this.results = results; }

    public long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public long getP95LatencyMs() { return p95LatencyMs; }
    public void setP95LatencyMs(long p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }

    public long getMaxLatencyMs() { return maxLatencyMs; }
    public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
