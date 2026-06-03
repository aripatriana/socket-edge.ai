package com.socket.edge.tester.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A collection of suites to run in sequence.
 *
 * Example:
 * <pre>
 * name: "Full Regression"
 * stopOnFail: false
 * suites:
 *   - suites/smoke_test.yaml
 *   - suites/regression_full.yaml
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SuiteCollection {

    private String name;
    private String description;
    private boolean stopOnFail = false;
    private List<String> suites;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isStopOnFail() { return stopOnFail; }
    public void setStopOnFail(boolean stopOnFail) { this.stopOnFail = stopOnFail; }

    public List<String> getSuites() { return suites; }
    public void setSuites(List<String> suites) { this.suites = suites; }
}
