package com.stocktrading.simulator.assistant.controller;

import com.stocktrading.simulator.assistant.config.AssistantProperties;
import com.stocktrading.simulator.assistant.dto.ChatRequest;
import com.stocktrading.simulator.assistant.dto.ConversationResponse;
import com.stocktrading.simulator.assistant.model.AgentResponse;
import com.stocktrading.simulator.assistant.model.Conversation;
import com.stocktrading.simulator.assistant.service.ConversationService;
import com.stocktrading.simulator.assistant.service.TradingAgentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing the AI Trading Assistant conversational and history APIs.
 *
 * <p><strong>Security & Safety Guarantees:</strong></p>
 * <ul>
 *   <li>All endpoints strictly require authentication.</li>
 *   <li>User identity is obtained exclusively via {@link Authentication#getName()}, never from request payloads.</li>
 *   <li>Zero trade execution: does not inject or invoke {@code TradeService}.</li>
 *   <li>Controlled error responses: zero information leakage about cross-user conversations.</li>
 *   <li>Assistant disabled guard: returns 503 SERVICE_UNAVAILABLE when {@code stocksim.assistant.enabled=false}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final TradingAgentService tradingAgentService;
    private final ConversationService conversationService;
    private final AssistantProperties assistantProperties;

    public AssistantController(TradingAgentService tradingAgentService,
                               ConversationService conversationService,
                               AssistantProperties assistantProperties) {
        this.tradingAgentService = tradingAgentService;
        this.conversationService = conversationService;
        this.assistantProperties = assistantProperties;
    }

    /**
     * Sends a natural language chat message to the AI Trading Assistant.
     *
     * @param authentication Spring Security authentication principal
     * @param request        validated chat request containing user message and optional conversationId
     * @return 200 OK with {@link AgentResponse}, or appropriate error status
     */
    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(
            Authentication authentication,
            @Valid @RequestBody ChatRequest request) {

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!assistantProperties.isEnabled()) {
            log.info("AI assistant request rejected: assistant is disabled in configuration.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    AgentResponse.error(
                            request != null ? request.getConversationId() : null,
                            "The StockSim AI Assistant is currently disabled in application configuration.",
                            "ASSISTANT_DISABLED"
                    )
            );
        }

        if (request == null || request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    AgentResponse.error(
                            request != null ? request.getConversationId() : null,
                            "User message cannot be null, empty, or whitespace-only",
                            "INVALID_ARGUMENT"
                    )
            );
        }

        if (request.getMessage().length() > 4000) {
            return ResponseEntity.badRequest().body(
                    AgentResponse.error(
                            request.getConversationId(),
                            "User message exceeds maximum length of 4000 characters",
                            "MESSAGE_TOO_LONG"
                    )
            );
        }

        if (request.getConversationId() != null && !request.getConversationId().isBlank()) {
            String trimmedConvId = request.getConversationId().trim();
            if (trimmedConvId.length() > 64 || !trimmedConvId.matches("^[a-zA-Z0-9_-]+$")) {
                return ResponseEntity.badRequest().body(
                        AgentResponse.error(
                                trimmedConvId,
                                "Conversation ID format is invalid",
                                "INVALID_CONVERSATION_ID"
                        )
                );
            }
        }

        String username = authentication.getName();
        String convId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                ? request.getConversationId().trim()
                : null;

        AgentResponse response = tradingAgentService.processUserMessage(username, request.getMessage().trim(), convId);

        if (!response.isSuccess()) {
            if ("ASSISTANT_DISABLED".equals(response.getError())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            if ("INVALID_CONVERSATION".equals(response.getError()) || "ACCESS_DENIED".equals(response.getError())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the authenticated user's conversation history for a given conversation ID.
     *
     * @param authentication Spring Security authentication principal
     * @param conversationId the conversation UUID
     * @return 200 OK with {@link ConversationResponse} if found and owned by user, 404 NOT_FOUND otherwise
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(
            Authentication authentication,
            @PathVariable String conversationId) {

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!assistantProperties.isEnabled()) {
            log.info("AI assistant conversation request rejected: assistant is disabled in configuration.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        if (conversationId == null || conversationId.isBlank() || conversationId.length() > 64
                || !conversationId.matches("^[a-zA-Z0-9_-]+$")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String username = authentication.getName();

        try {
            Conversation conversation = conversationService.getConversation(conversationId, username);
            return ResponseEntity.ok(ConversationResponse.from(conversation));
        } catch (IllegalArgumentException | SecurityException ex) {
            log.debug("Conversation '{}' not found or access denied for user '{}': {}", conversationId, username, ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
