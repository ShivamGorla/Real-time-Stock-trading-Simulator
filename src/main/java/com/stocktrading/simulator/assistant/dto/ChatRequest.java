package com.stocktrading.simulator.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for sending a natural language message to the AI Trading Assistant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {

    @NotBlank(message = "Message cannot be empty or blank")
    @Size(max = 4000, message = "Message must not exceed 4000 characters")
    private String message;

    @Size(max = 64, message = "Conversation ID must not exceed 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Conversation ID must contain only alphanumeric characters, underscores, or hyphens")
    private String conversationId;

    public ChatRequest() {}

    public ChatRequest(String message) {
        this.message = message;
    }

    public ChatRequest(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
