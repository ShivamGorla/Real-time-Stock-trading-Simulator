package com.stocktrading.simulator.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.UniversalLlmClient;
import com.stocktrading.simulator.assistant.client.exception.*;
import com.stocktrading.simulator.assistant.client.model.ChatCompletionRequest;
import com.stocktrading.simulator.assistant.client.model.ChatMessage;
import com.stocktrading.simulator.assistant.client.model.FunctionCall;
import com.stocktrading.simulator.assistant.client.model.ToolCall;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.config.AssistantProperties;
import com.stocktrading.simulator.assistant.model.Conversation;
import com.stocktrading.simulator.assistant.tool.*;
import com.stocktrading.simulator.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("Assistant Config, UniversalLlmClient & Conversation Memory Unit Tests")
class AssistantClientAndMemoryUnitTest {

    @Test
    @DisplayName("AssistantProperties defaults disabled, masks API key in toString, and validates only when enabled")
    void propertiesValidationAndMasking() {
        AssistantProperties props = new AssistantProperties();
        assertFalse(props.isEnabled());
        assertDoesNotThrow(props::validate);

        props.setEnabled(true);
        assertThrows(LlmConfigurationException.class, props::validate);

        props.setApiKey("sk-super-secret-value");
        assertDoesNotThrow(props::validate);
        assertThat(props.toString()).doesNotContain("sk-super-secret-value").contains("***");
    }

    @Test
    @DisplayName("UniversalLlmClient classifies HTTP 401, 429, 500, and disabled state without live network calls")
    void llmClientErrorClassification() {
        AssistantProperties disabledProps = new AssistantProperties();
        UniversalLlmClient dormant = new UniversalLlmClient(disabledProps, null, new ObjectMapper());
        assertFalse(dormant.isEnabled());
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .messages(List.of(ChatMessage.user("Hello"))).build();
        assertThrows(LlmConfigurationException.class, () -> dormant.chatCompletion(req));

        AssistantProperties enabledProps = new AssistantProperties();
        enabledProps.setEnabled(true);
        enabledProps.setApiKey("test-key");
        enabledProps.setBaseUrl("https://mock.provider.local/v1");
        enabledProps.setModel("llama-3.3-70b-versatile");

        RestClient.Builder builder = RestClient.builder().baseUrl(enabledProps.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UniversalLlmClient client = new UniversalLlmClient(enabledProps, builder.build(), new ObjectMapper());

        server.expect(requestTo("https://mock.provider.local/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid API key\"}}"));
        assertThrows(LlmAuthenticationException.class, () -> client.chatCompletion(req));

        server.reset();
        server.expect(requestTo("https://mock.provider.local/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThrows(LlmRateLimitException.class, () -> client.chatCompletion(req));

        server.reset();
        server.expect(requestTo("https://mock.provider.local/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThrows(LlmServerException.class, () -> client.chatCompletion(req));
    }

    @Test
    @DisplayName("Conversation enforces MAX_MESSAGES, MAX_CONTEXT_CHARS, and cross-user SecurityException")
    void conversationBoundsAndOwnership() {
        Conversation conv = new Conversation("c1", "alice");
        // Exceed MAX_MESSAGES (20)
        for (int i = 0; i < 25; i++) {
            conv.addMessage(ChatMessage.user("msg-" + i));
        }
        assertEquals(Conversation.MAX_MESSAGES, conv.getMessageCount());

        // Exceed MAX_CONTEXT_CHARS (32,000) with large messages
        Conversation bigConv = new Conversation("c2", "alice");
        String chunk = "x".repeat(10_000);
        bigConv.addMessage(ChatMessage.user(chunk));
        bigConv.addMessage(ChatMessage.assistant(chunk));
        bigConv.addMessage(ChatMessage.user(chunk));
        bigConv.addMessage(ChatMessage.assistant(chunk)); // 40,000 > 32,000 -> trims oldest
        assertTrue(bigConv.getTotalContextChars() <= Conversation.MAX_CONTEXT_CHARS);
        assertTrue(bigConv.getMessageCount() >= 1);

        // Cross-user access throws SecurityException
        ConversationService svc = new ConversationService();
        Conversation created = svc.createConversation("alice");
        assertThrows(SecurityException.class, () -> svc.getConversation(created.getId(), "bob"));
    }

    @Test
    @DisplayName("Sensitive internal exception details never leak into ToolResult across ToolRegistry and all 6 tools")
    void syntheticInternalExceptionSentinelNeverLeaksFromToolsOrRegistry() {
        String sentinel = "SENTINEL_DB_SECRET_999";
        RuntimeException leakyEx = new RuntimeException(
                "jdbc:mysql://internal-db:3306/prod password=" + sentinel + " SQL=SELECT * FROM users");
        ObjectMapper mapper = new ObjectMapper();

        // 1. Direct unhandled exception inside ToolRegistry
        AssistantTool throwingTool = new AssistantTool() {
            @Override public String getName() { return "leaky_test_tool"; }
            @Override public String getDescription() { return "test"; }
            @Override public ToolDefinition getToolDefinition() { return new ToolDefinition("leaky_test_tool", "test", Map.of("type", "object")); }
            @Override public ToolResult execute(String username, String argumentsJson) { throw leakyEx; }
        };
        ToolRegistry reg = new ToolRegistry(List.of(throwingTool));
        ToolResult regResult = reg.executeTool("leaky_test_tool", "alice", "{}");
        assertFalse(regResult.isSuccess());
        assertThat(regResult.getContent()).doesNotContain(sentinel, "jdbc:mysql", "SELECT * FROM users");

        // 2. All 6 production tools with mocked failing services
        PortfolioService portSvc = mock(PortfolioService.class);
        when(portSvc.getPortfolio(anyString(), anyBoolean())).thenThrow(leakyEx);

        WalletService walletSvc = mock(WalletService.class);
        when(walletSvc.getWalletResponseByUsername(anyString())).thenThrow(leakyEx);

        OrderService orderSvc = mock(OrderService.class);
        when(orderSvc.getUserOrders(anyString())).thenThrow(leakyEx);

        StockService stockSvc = mock(StockService.class);
        when(stockSvc.getStockBySymbol(anyString())).thenThrow(leakyEx);

        TradeProposalService proposalSvc = mock(TradeProposalService.class);
        when(proposalSvc.generateProposal(anyString(), anyString(), any(), anyInt())).thenThrow(leakyEx);

        List<ToolResult> results = List.of(
                new PortfolioTools(portSvc, mapper).execute("alice", "{}"),
                new HoldingsTools(portSvc, mapper).execute("alice", "{}"),
                new WalletTools(walletSvc, mapper).execute("alice", "{}"),
                new OrderTools(orderSvc, mapper).execute("alice", "{}"),
                new StockTools(stockSvc, mapper).execute("alice", "{\"symbol\":\"INFY\"}"),
                new TradeProposalTools(proposalSvc, mapper).execute("alice", "{\"symbol\":\"INFY\",\"side\":\"BUY\",\"quantity\":1}")
        );

        for (ToolResult res : results) {
            assertFalse(res.isSuccess());
            assertThat(res.getContent()).doesNotContain(sentinel, "jdbc:mysql", "SELECT * FROM users");
        }
    }

    @Test
    @DisplayName("Conversation truncation across consecutive multi-tool exchanges preserves complete tool-call sequences")
    void conversationTrimmingNeverLeavesPartialOrOrphanedToolCallSequences() {
        // Case 1: Trigger MAX_MESSAGES trimming with 8 consecutive multi-tool turns (40 messages total)
        Conversation conv = new Conversation("c-trim-msgs", "alice");
        for (int i = 0; i < 8; i++) {
            conv.addMessage(ChatMessage.user("Question " + i));
            ToolCall tc1 = new ToolCall("call-" + i + "-a", new FunctionCall("get_wallet_balance", "{}"));
            ToolCall tc2 = new ToolCall("call-" + i + "-b", new FunctionCall("get_holdings", "{}"));
            conv.addMessages(List.of(
                    ChatMessage.assistant(List.of(tc1, tc2)),
                    ChatMessage.tool("call-" + i + "-a", "get_wallet_balance", "{\"balance\":100000}"),
                    ChatMessage.tool("call-" + i + "-b", "get_holdings", "[]")
            ));
            conv.addMessage(ChatMessage.assistant("Answer " + i));
        }

        assertValidToolCallIntegrity(conv.getMessages());
        assertTrue(conv.getMessageCount() <= Conversation.MAX_MESSAGES);

        // Conversation can continue cleanly after trimming
        conv.addMessage(ChatMessage.user("Follow-up question"));
        conv.addMessage(ChatMessage.assistant("Follow-up answer"));
        assertValidToolCallIntegrity(conv.getMessages());

        // Case 2: Trigger MAX_CONTEXT_CHARS trimming with large tool payloads
        Conversation largeConv = new Conversation("c-trim-chars", "alice");
        String largePayload = "D".repeat(7_000);
        for (int i = 0; i < 5; i++) {
            largeConv.addMessage(ChatMessage.user("Big query " + i));
            ToolCall tc1 = new ToolCall("big-" + i + "-a", new FunctionCall("get_portfolio", "{}"));
            ToolCall tc2 = new ToolCall("big-" + i + "-b", new FunctionCall("get_order_history", "{}"));
            largeConv.addMessages(List.of(
                    ChatMessage.assistant(List.of(tc1, tc2)),
                    ChatMessage.tool("big-" + i + "-a", "get_portfolio", largePayload),
                    ChatMessage.tool("big-" + i + "-b", "get_order_history", largePayload)
            ));
            largeConv.addMessage(ChatMessage.assistant("Summary " + i));
        }

        assertValidToolCallIntegrity(largeConv.getMessages());
        assertTrue(largeConv.getTotalContextChars() <= Conversation.MAX_CONTEXT_CHARS);
    }

    private void assertValidToolCallIntegrity(List<ChatMessage> messages) {
        assertFalse(messages.isEmpty());
        assertNotEquals("tool", messages.get(0).getRole().toLowerCase(),
                "Conversation must never start with an orphaned tool message");

        int i = 0;
        while (i < messages.size()) {
            ChatMessage m = messages.get(i);
            assertNotEquals("tool", m.getRole().toLowerCase(),
                    "Orphaned tool message found without preceding assistant tool_calls at index " + i);
            if ("assistant".equalsIgnoreCase(m.getRole()) && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                Set<String> expectedIds = new HashSet<>();
                for (ToolCall tc : m.getToolCalls()) {
                    expectedIds.add(tc.getId());
                }
                Set<String> matchedIds = new HashSet<>();
                int j = i + 1;
                while (j < messages.size() && "tool".equalsIgnoreCase(messages.get(j).getRole())) {
                    matchedIds.add(messages.get(j).getToolCallId());
                    j++;
                }
                assertEquals(expectedIds, matchedIds,
                        "Every retained assistant tool_call must have its exact matching tool result(s)");
                i = j;
            } else {
                i++;
            }
        }
    }
}
