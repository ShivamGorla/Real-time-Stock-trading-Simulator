package com.stocktrading.simulator.assistant.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response payload returned by an OpenAI-compatible /v1/chat/completions endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionResponse {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    public ChatCompletionResponse() {}

    public ChatCompletionResponse(String id, String model, List<Choice> choices, Usage usage) {
        this.id = id;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
    }

    public String getFirstMessageContent() {
        if (choices != null && !choices.isEmpty() && choices.get(0).getMessage() != null) {
            return choices.get(0).getMessage().getContent();
        }
        return null;
    }

    public boolean hasToolCalls() {
        if (choices != null && !choices.isEmpty() && choices.get(0).getMessage() != null) {
            List<ToolCall> calls = choices.get(0).getMessage().getToolCalls();
            return calls != null && !calls.isEmpty();
        }
        return false;
    }

    public List<ToolCall> getFirstToolCalls() {
        if (hasToolCalls()) {
            return choices.get(0).getMessage().getToolCalls();
        }
        return List.of();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }
}
