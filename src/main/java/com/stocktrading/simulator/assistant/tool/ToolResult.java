package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Structured encapsulation of an assistant tool execution result.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {

    private final boolean success;
    private final String content;
    private final String errorMessage;

    private ToolResult(boolean success, String content, String errorMessage) {
        this.success = success;
        this.content = content;
        this.errorMessage = errorMessage;
    }

    public static ToolResult success(String jsonContent) {
        return new ToolResult(true, jsonContent != null ? jsonContent : "{}", null);
    }

    public static ToolResult error(String errorMessage) {
        String safeError = errorMessage != null ? errorMessage : "Unknown tool execution error";
        String jsonError = "{\"error\":\"" + escapeJson(safeError) + "\"}";
        return new ToolResult(false, jsonError, safeError);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "ToolResult{" +
                "success=" + success +
                ", content='" + content + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
