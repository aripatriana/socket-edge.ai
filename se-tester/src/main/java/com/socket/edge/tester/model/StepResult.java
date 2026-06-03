package com.socket.edge.tester.model;

import com.socket.edge.tester.core.iso.IsoMessage;

import java.util.List;

public class StepResult {

    public enum Status { PASSED, FAILED, SKIPPED, ERROR }

    private String stepId;
    private String stepName;
    private Status status = Status.PASSED;
    private long latencyMs;
    private IsoMessage request;
    private IsoMessage response;
    private List<AssertionResult> assertions;
    private List<StepResult> subSteps; // populated when action=CALL
    private String error;

    public static StepResult skipped(TestStep step) {
        StepResult r = new StepResult();
        r.stepId   = step.getId();
        r.stepName = step.getName();
        r.status   = Status.SKIPPED;
        return r;
    }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public IsoMessage getRequest() { return request; }
    public void setRequest(IsoMessage request) { this.request = request; }

    public IsoMessage getResponse() { return response; }
    public void setResponse(IsoMessage response) { this.response = response; }

    public List<AssertionResult> getAssertions() { return assertions; }
    public void setAssertions(List<AssertionResult> assertions) { this.assertions = assertions; }

    public List<StepResult> getSubSteps() { return subSteps; }
    public void setSubSteps(List<StepResult> subSteps) { this.subSteps = subSteps; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}