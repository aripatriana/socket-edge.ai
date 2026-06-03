package com.socket.edge.tester.model;

import java.util.ArrayList;
import java.util.List;

public class CollectionResult {

    public enum Status { PASSED, FAILED, ERROR }

    private String collectionName;
    private Status status;
    private long durationMs;
    private List<SuiteResult> suites = new ArrayList<>();

    private long avgLatencyMs;
    private long p95LatencyMs;
    private long maxLatencyMs;

    public CollectionResult(String collectionName) {
        this.collectionName = collectionName;
    }

    public boolean isPassed() { return status == Status.PASSED; }

    public int  getTotalSuites()  { return suites.size(); }
    public long getPassedSuites() { return suites.stream().filter(SuiteResult::isPassed).count(); }
    public long getFailedSuites() { return suites.stream().filter(s -> !s.isPassed()).count(); }

    public long getTotalCases()   { return suites.stream().mapToLong(SuiteResult::getTotalCases).sum(); }
    public long getPassedCases()  { return suites.stream().mapToLong(SuiteResult::getPassedCases).sum(); }
    public long getFailedCases()  { return suites.stream().mapToLong(SuiteResult::getFailedCases).sum(); }
    public long getErrorCases()   { return suites.stream().mapToLong(SuiteResult::getErrorCases).sum(); }
    public long getSkippedCases() { return suites.stream().mapToLong(SuiteResult::getSkippedCases).sum(); }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public List<SuiteResult> getSuites() { return suites; }
    public void setSuites(List<SuiteResult> suites) { this.suites = suites; }

    public long getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }

    public long getP95LatencyMs() { return p95LatencyMs; }
    public void setP95LatencyMs(long p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }

    public long getMaxLatencyMs() { return maxLatencyMs; }
    public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }
}
