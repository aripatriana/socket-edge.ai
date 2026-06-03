package com.socket.edge.tester.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestStep {

    public enum Action { SEND, SEND_ASYNC, WAIT, LOG, PAUSE, CALL, DISCONNECT }

    private String id;
    private String name;
    private Action action = Action.SEND;
    private boolean skipOnFail = false;

    // SEND
    private MessageDef message;
    private LatencySla latencySla;
    private List<Assertion> assertions;

    // SEND / DISCONNECT — which named connection to use (default: "default")
    private String connection = "default";

    // CALL
    private String keyword;
    private Map<String, String> params; // param overrides passed to the keyword

    // WAIT / PAUSE
    private Long waitMs;
    private Long pauseMs;

    // LOG
    private String logMessage;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageDef {
        private String mti;
        private Map<String, String> fields;

        public String getMti() { return mti; }
        public void setMti(String mti) { this.mti = mti; }
        public Map<String, String> getFields() { return fields; }
        public void setFields(Map<String, String> fields) { this.fields = fields; }
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : id; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public boolean isSkipOnFail() { return skipOnFail; }
    public void setSkipOnFail(boolean skipOnFail) { this.skipOnFail = skipOnFail; }

    public MessageDef getMessage() { return message; }
    public void setMessage(MessageDef message) { this.message = message; }

    public LatencySla getLatencySla() { return latencySla; }
    public void setLatencySla(LatencySla latencySla) { this.latencySla = latencySla; }

    public List<Assertion> getAssertions() { return assertions; }
    public void setAssertions(List<Assertion> assertions) { this.assertions = assertions; }

    public String getConnection() { return connection != null ? connection : "default"; }
    public void setConnection(String connection) { this.connection = connection; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }

    public Long getWaitMs() { return waitMs; }
    public void setWaitMs(Long waitMs) { this.waitMs = waitMs; }

    public Long getPauseMs() { return pauseMs; }
    public void setPauseMs(Long pauseMs) { this.pauseMs = pauseMs; }

    public String getLogMessage() { return logMessage; }
    public void setLogMessage(String logMessage) { this.logMessage = logMessage; }
}