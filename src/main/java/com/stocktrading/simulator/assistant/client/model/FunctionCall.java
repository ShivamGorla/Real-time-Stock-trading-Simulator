package com.stocktrading.simulator.assistant.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Details of a function call invocation requested by the model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionCall {

    private String name;
    private String arguments;

    public FunctionCall() {}

    public FunctionCall(String name, String arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }
}
