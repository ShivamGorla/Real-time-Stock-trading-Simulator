package com.stocktrading.simulator.assistant.tool;

import com.stocktrading.simulator.assistant.client.model.ToolDefinition;

/**
 * Common contract for all AI Assistant tools.
 * Exposes OpenAPI/JSON schema definitions and safe execution with server-injected user context.
 */
public interface AssistantTool {

    /**
     * Unique stable name of the tool (e.g. get_portfolio, preview_trade_proposal).
     */
    String getName();

    /**
     * Detailed human-readable description describing tool behavior to the LLM.
     */
    String getDescription();

    /**
     * OpenAI-compatible {@link ToolDefinition} containing JSON Schema parameter specifications.
     */
    ToolDefinition getToolDefinition();

    /**
     * Executes the tool using server-injected user context and LLM-supplied JSON arguments.
     *
     * @param username      the authenticated username supplied by Spring Security (never from LLM JSON)
     * @param argumentsJson the JSON string of arguments provided by the LLM
     * @return structured {@link ToolResult} containing serialized output or error details
     */
    ToolResult execute(String username, String argumentsJson);
}
