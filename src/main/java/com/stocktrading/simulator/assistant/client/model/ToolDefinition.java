package com.stocktrading.simulator.assistant.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Top-level tool specification provided in a ChatCompletionRequest.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    private String type = "function";
    private FunctionDefinition function;

    public ToolDefinition() {}

    public ToolDefinition(FunctionDefinition function) {
        this.type = "function";
        this.function = function;
    }

    public ToolDefinition(String name, String description, Object parameters) {
        this.type = "function";
        this.function = new FunctionDefinition(name, description, parameters);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    public void setFunction(FunctionDefinition function) {
        this.function = function;
    }
}
