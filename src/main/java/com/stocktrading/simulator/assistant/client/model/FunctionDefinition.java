package com.stocktrading.simulator.assistant.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Definition of a callable function including name, description, and JSON schema parameters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionDefinition {

    private String name;
    private String description;
    private Object parameters;

    public FunctionDefinition() {}

    public FunctionDefinition(String name, String description, Object parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getParameters() {
        return parameters;
    }

    public void setParameters(Object parameters) {
        this.parameters = parameters;
    }
}
