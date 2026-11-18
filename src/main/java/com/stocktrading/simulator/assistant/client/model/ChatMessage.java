package com.stocktrading.simulator.assistant.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Message object conforming to the OpenAI Chat Completions message schema.
 * Supports system, user, assistant (with or without tool_calls), and tool responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    private String role;
    private String content;
    private String name;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public ChatMessage(String role, String content, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
    }

    public ChatMessage(String role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage assistant(List<ToolCall> toolCalls) {
        return assistant(null, toolCalls);
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        ChatMessage msg = new ChatMessage("assistant", content);
        msg.setToolCalls(toolCalls);
        return msg;
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId);
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        ChatMessage msg = new ChatMessage("tool", content, toolCallId);
        msg.setName(name);
        return msg;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
