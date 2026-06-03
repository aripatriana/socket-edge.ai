package com.socket.edge.tester.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Assertion {

    public enum Severity { HARD, SOFT, INFO }

    // MTI check
    private String mti;

    // Field checks
    private String field;
    private String equals;
    private List<String> in;
    private String notEquals;
    private Boolean notEmpty;
    private Boolean exists;
    private String matches;
    private String equalsField;
    private String greaterThan;
    private Between between;

    // Latency check
    private LatencySla latency;

    // JS expression (Phase 2 — parsed but skipped at runtime for now)
    private String expression;

    // Conditional
    private String onlyIf;

    private Severity severity = Severity.HARD;
    private String message;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Between {
        private String min;
        private String max;
        public String getMin() { return min; }
        public void setMin(String min) { this.min = min; }
        public String getMax() { return max; }
        public void setMax(String max) { this.max = max; }
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getMti() { return mti; }
    public void setMti(String mti) { this.mti = mti; }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getEquals() { return equals; }
    public void setEquals(String equals) { this.equals = equals; }

    public List<String> getIn() { return in; }
    public void setIn(List<String> in) { this.in = in; }

    public String getNotEquals() { return notEquals; }
    public void setNotEquals(String notEquals) { this.notEquals = notEquals; }

    public Boolean getNotEmpty() { return notEmpty; }
    public void setNotEmpty(Boolean notEmpty) { this.notEmpty = notEmpty; }

    public Boolean getExists() { return exists; }
    public void setExists(Boolean exists) { this.exists = exists; }

    public String getMatches() { return matches; }
    public void setMatches(String matches) { this.matches = matches; }

    public String getEqualsField() { return equalsField; }
    public void setEqualsField(String equalsField) { this.equalsField = equalsField; }

    public String getGreaterThan() { return greaterThan; }
    public void setGreaterThan(String greaterThan) { this.greaterThan = greaterThan; }

    public Between getBetween() { return between; }
    public void setBetween(Between between) { this.between = between; }

    public LatencySla getLatency() { return latency; }
    public void setLatency(LatencySla latency) { this.latency = latency; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getOnlyIf() { return onlyIf; }
    public void setOnlyIf(String onlyIf) { this.onlyIf = onlyIf; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}