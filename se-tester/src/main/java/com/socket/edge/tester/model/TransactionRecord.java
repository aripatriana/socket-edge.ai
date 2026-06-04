package com.socket.edge.tester.model;

public class TransactionRecord {

    private final long timestampMs;
    private final long latencyMs;
    private final String status; // SUCCESS | TIMEOUT | ERROR

    public TransactionRecord(long timestampMs, long latencyMs, String status) {
        this.timestampMs = timestampMs;
        this.latencyMs   = latencyMs;
        this.status      = status;
    }

    public long getTimestampMs() { return timestampMs; }
    public long getLatencyMs()   { return latencyMs; }
    public String getStatus()    { return status; }
}
