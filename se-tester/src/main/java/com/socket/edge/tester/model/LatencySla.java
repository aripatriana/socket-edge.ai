package com.socket.edge.tester.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LatencySla {
    private long warn;   // ms — SOFT assertion
    private long fail;   // ms — HARD assertion

    public long getWarn() { return warn; }
    public void setWarn(long warn) { this.warn = warn; }

    public long getFail() { return fail; }
    public void setFail(long fail) { this.fail = fail; }
}