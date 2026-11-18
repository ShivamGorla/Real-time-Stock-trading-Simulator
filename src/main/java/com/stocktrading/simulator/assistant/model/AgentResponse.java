package com.stocktrading.simulator.assistant.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured response returned by {@code TradingAgentService} after processing a user message.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    private String conversationId;
    private String message;
    private List<String> toolsUsed = new ArrayList<>();
    private boolean success;
    private String error;
    private Instant timestamp;

    public AgentResponse() {}

    public AgentResponse(String conversationId,
                         String message,
                         List<String> toolsUsed,
                         boolean success,
                         String error,
                         Instant timestamp) {
        this.conversationId = conversationId;
        this.message = message;
        this.toolsUsed = toolsUsed != null ? new ArrayList<>(toolsUsed) : new ArrayList<>();
        this.success = success;
        this.error = error;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static AgentResponse success(String conversationId, String message, List<String> toolsUsed) {
        return new AgentResponse(conversationId, message, toolsUsed, true, null, Instant.now());
    }

    public static AgentResponse error(String conversationId, String errorMessage, String errorCode) {
        return new AgentResponse(conversationId, errorMessage, Collections.emptyList(), false, errorCode, Instant.now());
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getToolsUsed() {
        return Collections.unmodifiableList(toolsUsed);
    }

    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed != null ? new ArrayList<>(toolsUsed) : new ArrayList<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
