package com.stocktrading.simulator.assistant.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A tool call requested by the model in an assistant response message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall {

    private String id;
    private String type = "function";
    private FunctionCall function;

    public ToolCall() {}

    public ToolCall(String id, String type, FunctionCall function) {
        this.id = id;
        this.type = type != null ? type : "function";
        this.function = function;
    }

    public ToolCall(String id, FunctionCall function) {
        this(id, "function", function);
    }

    public ToolCall(String id, String functionName, String arguments) {
        this.id = id;
        this.type = "function";
        this.function = new FunctionCall(functionName, arguments);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionCall getFunction() {
        return function;
    }

    public void setFunction(FunctionCall function) {
        this.function = function;
    }
}
