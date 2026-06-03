package com.socket.edge.tester.model;

public class AssertionResult {

    private String description;
    private Assertion.Severity severity = Assertion.Severity.HARD;
    private boolean passed;
    private String actualValue;
    private String expectedValue;
    private String message; // custom message from assertion config

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Assertion.Severity getSeverity() { return severity; }
    public void setSeverity(Assertion.Severity severity) { this.severity = severity; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getActualValue() { return actualValue; }
    public void setActualValue(String actualValue) { this.actualValue = actualValue; }

    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}