package com.socket.edge.tester.model;

import java.util.List;

public class StressResult {

    private final List<StressStepResult> steps;
    private final int maxSustainableTps;
    private final boolean breakingPointFound;
    private final long startTimeMs;

    public StressResult(List<StressStepResult> steps, int maxSustainableTps,
                        boolean breakingPointFound, long startTimeMs) {
        this.steps               = steps;
        this.maxSustainableTps   = maxSustainableTps;
        this.breakingPointFound  = breakingPointFound;
        this.startTimeMs         = startTimeMs;
    }

    public List<StressStepResult> getSteps()         { return steps; }
    public int getMaxSustainableTps()                { return maxSustainableTps; }
    public boolean isBreakingPointFound()            { return breakingPointFound; }
    public long getStartTimeMs()                     { return startTimeMs; }
}
