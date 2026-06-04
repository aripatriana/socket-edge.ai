package com.socket.edge.tester.model;

import java.util.List;

public class LoadResult {

    private final long startTimeMs;
    private final long durationMs;
    private final int totalSent;
    private final int totalSuccess;
    private final int totalError;
    private final int totalTimeout;
    private final double achievedTps;
    private final long latencyMin;
    private final long latencyAvg;
    private final long latencyMax;
    private final long latencyP50;
    private final long latencyP90;
    private final long latencyP95;
    private final long latencyP99;
    private final List<TransactionRecord> records;

    public LoadResult(long startTimeMs, long durationMs,
                      int totalSent, int totalSuccess, int totalError, int totalTimeout,
                      double achievedTps,
                      long latencyMin, long latencyAvg, long latencyMax,
                      long latencyP50, long latencyP90, long latencyP95, long latencyP99,
                      List<TransactionRecord> records) {
        this.startTimeMs  = startTimeMs;
        this.durationMs   = durationMs;
        this.totalSent    = totalSent;
        this.totalSuccess = totalSuccess;
        this.totalError   = totalError;
        this.totalTimeout = totalTimeout;
        this.achievedTps  = achievedTps;
        this.latencyMin   = latencyMin;
        this.latencyAvg   = latencyAvg;
        this.latencyMax   = latencyMax;
        this.latencyP50   = latencyP50;
        this.latencyP90   = latencyP90;
        this.latencyP95   = latencyP95;
        this.latencyP99   = latencyP99;
        this.records      = records;
    }

    public long getStartTimeMs()  { return startTimeMs; }
    public long getDurationMs()   { return durationMs; }
    public int getTotalSent()     { return totalSent; }
    public int getTotalSuccess()  { return totalSuccess; }
    public int getTotalError()    { return totalError; }
    public int getTotalTimeout()  { return totalTimeout; }
    public double getAchievedTps(){ return achievedTps; }
    public long getLatencyMin()   { return latencyMin; }
    public long getLatencyAvg()   { return latencyAvg; }
    public long getLatencyMax()   { return latencyMax; }
    public long getLatencyP50()   { return latencyP50; }
    public long getLatencyP90()   { return latencyP90; }
    public long getLatencyP95()   { return latencyP95; }
    public long getLatencyP99()   { return latencyP99; }
    public List<TransactionRecord> getRecords() { return records; }

    public double getSuccessRate() {
        return totalSent == 0 ? 0.0 : (double) totalSuccess / totalSent * 100.0;
    }
}
