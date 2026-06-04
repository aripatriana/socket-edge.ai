package com.socket.edge.tester.model;

public class StressStepResult {

    private final int stepNum;
    private final int targetTps;
    private final int totalSent;
    private final int totalSuccess;
    private final int totalError;
    private final int totalTimeout;
    private final long latencyMin;
    private final long latencyAvg;
    private final long latencyMax;
    private final long latencyP50;
    private final long latencyP90;
    private final long latencyP95;
    private final long latencyP99;
    private final boolean breakingPoint;

    public StressStepResult(int stepNum, int targetTps,
                            int totalSent, int totalSuccess, int totalError, int totalTimeout,
                            long latencyMin, long latencyAvg, long latencyMax,
                            long latencyP50, long latencyP90, long latencyP95, long latencyP99,
                            boolean breakingPoint) {
        this.stepNum       = stepNum;
        this.targetTps     = targetTps;
        this.totalSent     = totalSent;
        this.totalSuccess  = totalSuccess;
        this.totalError    = totalError;
        this.totalTimeout  = totalTimeout;
        this.latencyMin    = latencyMin;
        this.latencyAvg    = latencyAvg;
        this.latencyMax    = latencyMax;
        this.latencyP50    = latencyP50;
        this.latencyP90    = latencyP90;
        this.latencyP95    = latencyP95;
        this.latencyP99    = latencyP99;
        this.breakingPoint = breakingPoint;
    }

    public int getStepNum()       { return stepNum; }
    public int getTargetTps()     { return targetTps; }
    public int getTotalSent()     { return totalSent; }
    public int getTotalSuccess()  { return totalSuccess; }
    public int getTotalError()    { return totalError; }
    public int getTotalTimeout()  { return totalTimeout; }
    public long getLatencyMin()   { return latencyMin; }
    public long getLatencyAvg()   { return latencyAvg; }
    public long getLatencyMax()   { return latencyMax; }
    public long getLatencyP50()   { return latencyP50; }
    public long getLatencyP90()   { return latencyP90; }
    public long getLatencyP95()   { return latencyP95; }
    public long getLatencyP99()   { return latencyP99; }
    public boolean isBreakingPoint() { return breakingPoint; }

    public double getErrorRate() {
        return totalSent == 0 ? 0.0
            : (double)(totalError + totalTimeout) / totalSent * 100.0;
    }

    public double getAchievedTps() {
        return totalSent; // caller divides by stepDurationSec
    }
}
