package com.stocktrading.simulator.assistant.service;

import com.stocktrading.simulator.assistant.client.LlmClient;
import com.stocktrading.simulator.assistant.client.exception.*;
import com.stocktrading.simulator.assistant.client.model.*;
import com.stocktrading.simulator.assistant.config.AssistantProperties;
import com.stocktrading.simulator.assistant.model.AgentResponse;
import com.stocktrading.simulator.assistant.model.Conversation;
import com.stocktrading.simulator.assistant.tool.ToolRegistry;
import com.stocktrading.simulator.assistant.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service orchestrating AI Trading Assistant conversations and tool-calling execution.
 *
 * <p><strong>Safety & Architectural Invariants:</strong></p>
 * <ul>
 *   <li><strong>Zero Trade Execution:</strong> This service does NOT inject {@code TradeService} and cannot execute BUY or SELL trades.</li>
 *   <li><strong>Server-Injected Identity:</strong> The authenticated username is supplied from Spring Security context, never from LLM arguments.</li>
 *   <li><strong>Allowlisted Tool Execution:</strong> Only tools explicitly registered in {@link ToolRegistry} can execute.</li>
 *   <li><strong>Loop Guard:</strong> Caps tool-calling iterations at {@value #MAX_ITERATIONS} to prevent infinite loops.</li>
 *   <li><strong>Safe Error Containment:</strong> Internal exceptions are converted into controlled user responses without leaking stack traces or credentials.</li>
 * </ul>
 */
@Service
public class TradingAgentService {

    private static final Logger log = LoggerFactory.getLogger(TradingAgentService.class);
    public static final int MAX_ITERATIONS = 5;
    public static final int MAX_TOOL_CALLS_PER_TURN = 8;
    public static final int MAX_TOOL_RESULT_CHARS = 16_000;
    public static final int MAX_TURN_TOOL_RESULT_CHARS = 16_000;
    private static final String TRUNCATION_SUFFIX = "... [truncated]";

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ConversationService conversationService;
    private final AssistantProperties properties;

    public TradingAgentService(LlmClient llmClient,
                               ToolRegistry toolRegistry,
                               ConversationService conversationService,
                               AssistantProperties properties) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.conversationService = conversationService;
        this.properties = properties;
    }

    /**
     * Processes a user's natural language input within an existing or newly initialized conversation session.
     *
     * @param username       the authenticated username from server-side security context
     * @param userMessage    the natural language question or instruction
     * @param conversationId optional conversation session ID (null or blank creates a new session)
     * @return structured {@link AgentResponse}
     */
    public AgentResponse processUserMessage(String username, String userMessage, String conversationId) {
        if (username == null || username.isBlank()) {
            return AgentResponse.error(conversationId, "Authenticated username cannot be null or empty", "INVALID_ARGUMENT");
        }
        if (userMessage == null || userMessage.isBlank()) {
            return AgentResponse.error(conversationId, "User message cannot be null or empty", "INVALID_ARGUMENT");
        }

        Conversation conversation;
        try {
            conversation = conversationService.getOrCreateConversation(conversationId, username);
        } catch (SecurityException ex) {
            log.warn("Cross-user conversation access denied for user '{}'", username);
            return AgentResponse.error(conversationId, "Conversation not found.", "ACCESS_DENIED");
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid conversation lookup for user '{}'", username);
            return AgentResponse.error(conversationId, "Conversation not found.", "INVALID_CONVERSATION");
        }

        if (!llmClient.isEnabled()) {
            log.info("AI assistant request received while assistant is disabled.");
            String disabledMsg = "The StockSim AI Assistant is currently disabled in application configuration.";
            return AgentResponse.error(conversation.getId(), disabledMsg, "ASSISTANT_DISABLED");
        }

        // Serialize concurrent turns on the same conversationId to prevent interleaved histories,
        // without blocking unrelated conversations or cross-user ownership rejection above.
        conversation.getTurnLock().lock();
        try {
            // Add user message to conversation history
            conversation.addMessage(ChatMessage.user(userMessage));

            List<ToolDefinition> toolDefinitions = toolRegistry.getToolDefinitions();
            List<String> executedTools = new ArrayList<>();
            int iterations = 0;

            while (iterations < MAX_ITERATIONS) {
                iterations++;

                List<ChatMessage> fullMessages = new ArrayList<>();
                fullMessages.add(ChatMessage.system(StockSimSystemPrompt.SYSTEM_PROMPT));
                fullMessages.addAll(conversation.getMessages());

                ChatCompletionRequest request = ChatCompletionRequest.builder()
                        .model(properties.getModel())
                        .messages(fullMessages)
                        .tools(toolDefinitions != null && !toolDefinitions.isEmpty() ? toolDefinitions : null)
                        .toolChoice(toolDefinitions != null && !toolDefinitions.isEmpty() ? "auto" : null)
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .build();

                ChatCompletionResponse response;
                try {
                    response = llmClient.chatCompletion(request);
                } catch (LlmAuthenticationException ex) {
                    log.warn("LLM provider authentication failure (HTTP {})", ex.getStatusCode());
                    return AgentResponse.error(conversation.getId(), "LLM authentication failed. Please check configured API credentials.", "LLM_AUTHENTICATION_ERROR");
                } catch (LlmRateLimitException ex) {
                    log.warn("LLM provider rate limit exceeded (HTTP 429)");
                    return AgentResponse.error(conversation.getId(), "The AI assistant is experiencing high demand. Please try again in a few moments.", "LLM_RATE_LIMIT_ERROR");
                } catch (LlmTimeoutException ex) {
                    log.warn("LLM provider request timed out");
                    return AgentResponse.error(conversation.getId(), "The AI request timed out. Please try again.", "LLM_TIMEOUT_ERROR");
                } catch (LlmServerException ex) {
                    log.warn("LLM provider server error (HTTP {})", ex.getStatusCode());
                    return AgentResponse.error(conversation.getId(), "The AI provider is temporarily unavailable. Please try again shortly.", "LLM_SERVER_ERROR");
                } catch (LlmException ex) {
                    log.warn("LLM service error: {}", ex.getClass().getSimpleName());
                    return AgentResponse.error(conversation.getId(), "The AI service encountered an error processing your request. Please try again.", "LLM_SERVICE_ERROR");
                } catch (Exception ex) {
                    log.error("Unexpected error during assistant chat completion: {}", ex.getClass().getSimpleName());
                    return AgentResponse.error(conversation.getId(), "Unexpected error communicating with AI assistant. Please try again.", "UNEXPECTED_ERROR");
                }

                if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                    String emptyMsg = "Received empty response from the AI provider.";
                    conversation.addMessage(ChatMessage.assistant(emptyMsg));
                    return AgentResponse.error(conversation.getId(), emptyMsg, "EMPTY_RESPONSE");
                }

                Choice choice = response.getChoices().get(0);
                ChatMessage assistantMessage = choice.getMessage();

                if (assistantMessage == null) {
                    String emptyMsg = "Received empty assistant message from the AI provider.";
                    conversation.addMessage(ChatMessage.assistant(emptyMsg));
                    return AgentResponse.error(conversation.getId(), emptyMsg, "EMPTY_MESSAGE");
                }

                // Check if model requested tool calls
                List<ToolCall> requestedCalls = assistantMessage.getToolCalls();
                if (requestedCalls != null && !requestedCalls.isEmpty()) {
                    if (requestedCalls.size() > MAX_TOOL_CALLS_PER_TURN) {
                        log.warn("Agent rejected excessive tool calls ({} > {}) in a single turn for user '{}'",
                                requestedCalls.size(), MAX_TOOL_CALLS_PER_TURN, username);
                        String tooManyMsg = "The AI requested too many tool calls in a single step. Please narrow your question.";
                        conversation.addMessage(ChatMessage.assistant(tooManyMsg));
                        return new AgentResponse(conversation.getId(), tooManyMsg, executedTools, false, "TOO_MANY_TOOL_CALLS", Instant.now());
                    }

                    // Validate tool call IDs are present and distinct before executing or storing
                    Set<String> seenIds = new HashSet<>();
                    for (ToolCall tc : requestedCalls) {
                        if (tc == null || tc.getId() == null || tc.getId().isBlank() || !seenIds.add(tc.getId())) {
                            log.warn("Agent rejected malformed tool call payload for user '{}'", username);
                            String malformedMsg = "The AI returned a malformed tool request. Please try again.";
                            conversation.addMessage(ChatMessage.assistant(malformedMsg));
                            return new AgentResponse(conversation.getId(), malformedMsg, executedTools, false, "INVALID_TOOL_CALL", Instant.now());
                        }
                    }

                    List<ChatMessage> toolTurnBatch = new ArrayList<>(requestedCalls.size() + 1);
                    toolTurnBatch.add(assistantMessage);

                    int remainingTurnBudget = MAX_TURN_TOOL_RESULT_CHARS;
                    int totalCalls = requestedCalls.size();
                    for (int idx = 0; idx < totalCalls; idx++) {
                        ToolCall toolCall = requestedCalls.get(idx);
                        String toolCallId = toolCall.getId();
                        String toolName = (toolCall.getFunction() != null) ? toolCall.getFunction().getName() : null;
                        String toolArgs = (toolCall.getFunction() != null) ? toolCall.getFunction().getArguments() : "{}";

                        executedTools.add(toolName);
                        log.debug("Agent executing requested tool '{}' with id '{}'", toolName, toolCallId);

                        ToolResult toolResult = toolRegistry.executeTool(toolName, username, toolArgs);
                        int remainingCalls = totalCalls - idx;
                        int perCallAllowance = Math.min(MAX_TOOL_RESULT_CHARS, Math.max(0, remainingTurnBudget / remainingCalls));
                        String boundedContent = truncateToLimit(toolResult.getContent(), perCallAllowance);
                        remainingTurnBudget -= boundedContent.length();

                        toolTurnBatch.add(ChatMessage.tool(toolCallId, toolName, boundedContent));
                    }
                    // Add assistant(tool_calls) + all matching tool results atomically so history trimming
                    // never splits an active tool-call exchange
                    conversation.addMessages(toolTurnBatch);
                    // Continue loop with tool results included in next request
                } else {
                    // Final text completion response
                    String replyContent = assistantMessage.getContent() != null ? assistantMessage.getContent() : "";
                    conversation.addMessage(ChatMessage.assistant(replyContent));
                    return AgentResponse.success(conversation.getId(), replyContent, executedTools);
                }
            }

            // Loop terminated due to reaching MAX_ITERATIONS
            log.warn("Agent reached maximum allowed tool iterations ({}) for user '{}'", MAX_ITERATIONS, username);
            String limitMessage = "I have reached the maximum processing limit for this request. Please clarify or narrow down your question.";
            conversation.addMessage(ChatMessage.assistant(limitMessage));
            return new AgentResponse(conversation.getId(), limitMessage, executedTools, false, "MAX_ITERATIONS_REACHED", Instant.now());
        } finally {
            conversation.getTurnLock().unlock();
        }
    }

    private static String truncateToLimit(String content, int maxChars) {
        if (content == null || maxChars <= 0) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        if (maxChars <= TRUNCATION_SUFFIX.length()) {
            return content.substring(0, maxChars);
        }
        return content.substring(0, maxChars - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
    }
}
