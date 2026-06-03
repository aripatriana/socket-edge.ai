package com.socket.edge.tester.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Reusable named operation — loaded from a YAML file and invoked via action: CALL.
 *
 * Example:
 * <pre>
 * name: "sign_on"
 * parameters:
 *   TERMINAL_ID: "TERM0001"
 * steps:
 *   - id: signon
 *     action: SEND
 *     message:
 *       mti: "0800"
 *       fields:
 *         DE70: "101"
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Keyword {

    private String name;
    private String description;
    private Map<String, String> parameters; // default param values, overridable by caller
    private List<TestStep> steps;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }

    public List<TestStep> getSteps() { return steps; }
    public void setSteps(List<TestStep> steps) { this.steps = steps; }
}
