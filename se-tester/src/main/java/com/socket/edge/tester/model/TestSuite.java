package com.socket.edge.tester.model;

import java.util.List;

public class TestSuite {

    private String name;
    private String description;
    private List<String> tags;
    private boolean stopOnFail = false;
    private List<String> testCases;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public boolean isStopOnFail() { return stopOnFail; }
    public void setStopOnFail(boolean stopOnFail) { this.stopOnFail = stopOnFail; }

    public List<String> getTestCases() { return testCases; }
    public void setTestCases(List<String> testCases) { this.testCases = testCases; }
}
