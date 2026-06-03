package com.socket.edge.tester.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Root YAML model for a single test case.
 *
 * Example:
 * <pre>
 * name: "TC_001_Purchase_Happy_Path"
 * setup:
 *   server:
 *     port: 9100
 *     autoRespond: true
 *   connect:
 *     host: "{{env.SE_HOST}}"
 *     port: 9999
 * steps:
 *   - id: purchase
 *     action: SEND
 *     message:
 *       mti: "0200"
 *       fields:
 *         DE11: "{{stan()}}"
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCase {

    private String name;
    private String description;
    private List<String> tags;
    private long timeout = 30000; // ms per SEND step

    private String dataFile; // relative path to CSV file; if set, TC is run once per row
    private Setup setup;
    private Map<String, String> variables;
    private List<TestStep> steps;
    private Sla sla;
    private Teardown teardown;

    // -------------------------------------------------------------------------
    // Nested config classes
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Setup {
        private ServerConfig server;
        private ConnectConfig connect;

        public ServerConfig getServer() { return server; }
        public void setServer(ServerConfig server) { this.server = server; }
        public ConnectConfig getConnect() { return connect; }
        public void setConnect(ConnectConfig connect) { this.connect = connect; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerConfig {
        private int port = 9100;
        private boolean autoRespond = true;
        private int delayMs = 0;
        private String responseCode = "00"; // DE39 default; override for decline scenarios

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public boolean isAutoRespond() { return autoRespond; }
        public void setAutoRespond(boolean autoRespond) { this.autoRespond = autoRespond; }
        public int getDelayMs() { return delayMs; }
        public void setDelayMs(int delayMs) { this.delayMs = delayMs; }
        public String getResponseCode() { return responseCode; }
        public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectConfig {
        private String host = "127.0.0.1";
        private int port = 9999;
        private int timeoutMs = 10000;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sla {
        private long avgLatencyMs;
        private long p95LatencyMs;
        private long maxLatencyMs;
        private double minSuccessRate = 100.0;

        public long getAvgLatencyMs() { return avgLatencyMs; }
        public void setAvgLatencyMs(long avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
        public long getP95LatencyMs() { return p95LatencyMs; }
        public void setP95LatencyMs(long p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }
        public long getMaxLatencyMs() { return maxLatencyMs; }
        public void setMaxLatencyMs(long maxLatencyMs) { this.maxLatencyMs = maxLatencyMs; }
        public double getMinSuccessRate() { return minSuccessRate; }
        public void setMinSuccessRate(double minSuccessRate) { this.minSuccessRate = minSuccessRate; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Teardown {
        private boolean disconnect = true;
        private boolean stopServer = true;

        public boolean isDisconnect() { return disconnect; }
        public void setDisconnect(boolean disconnect) { this.disconnect = disconnect; }
        public boolean isStopServer() { return stopServer; }
        public void setStopServer(boolean stopServer) { this.stopServer = stopServer; }
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public String getDataFile() { return dataFile; }
    public void setDataFile(String dataFile) { this.dataFile = dataFile; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public long getTimeout() { return timeout; }
    public void setTimeout(long timeout) { this.timeout = timeout; }

    public Setup getSetup() { return setup; }
    public void setSetup(Setup setup) { this.setup = setup; }

    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }

    public List<TestStep> getSteps() { return steps; }
    public void setSteps(List<TestStep> steps) { this.steps = steps; }

    public Sla getSla() { return sla; }
    public void setSla(Sla sla) { this.sla = sla; }

    public Teardown getTeardown() { return teardown; }
    public void setTeardown(Teardown teardown) { this.teardown = teardown; }
}