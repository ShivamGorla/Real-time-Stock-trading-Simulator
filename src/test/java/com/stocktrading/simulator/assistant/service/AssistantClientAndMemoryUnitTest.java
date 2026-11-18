package com.stocktrading.simulator.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.LlmClient;
import com.stocktrading.simulator.assistant.client.UniversalLlmClient;
import com.stocktrading.simulator.assistant.client.exception.*;
import com.stocktrading.simulator.assistant.client.model.ChatCompletionRequest;
import com.stocktrading.simulator.assistant.client.model.ChatCompletionResponse;
import com.stocktrading.simulator.assistant.client.model.ChatMessage;
import com.stocktrading.simulator.assistant.client.model.Choice;
import com.stocktrading.simulator.assistant.client.model.FunctionCall;
import com.stocktrading.simulator.assistant.client.model.ToolCall;
import com.stocktrading.simulator.assistant.client.model.ToolDefinition;
import com.stocktrading.simulator.assistant.config.AssistantProperties;
import com.stocktrading.simulator.assistant.model.AgentResponse;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        when(orderSvc.getUserOrdersPage(anyString(), any(), any(), anyInt(), anyInt())).thenThrow(leakyEx);

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

    @Test
    @DisplayName("Conversation strictly enforces MAX_MESSAGES and MAX_CONTEXT_CHARS even for a single oversized block")
    void oversizedSingleToolCallBlockAndSingleMessageStillRespectBounds() {
        // Case A: Single tool-call block with 25 tool calls (26 messages > MAX_MESSAGES=20)
        Conversation overMsgConv = new Conversation("c-over-msg", "alice");
        List<ToolCall> manyCalls = new ArrayList<>();
        List<ChatMessage> oversizedBatch = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            manyCalls.add(new ToolCall("c-" + i, new FunctionCall("get_wallet_balance", "{}")));
        }
        oversizedBatch.add(ChatMessage.assistant(manyCalls));
        for (int i = 0; i < 25; i++) {
            oversizedBatch.add(ChatMessage.tool("c-" + i, "get_wallet_balance", "{}"));
        }
        overMsgConv.addMessages(oversizedBatch);
        assertTrue(overMsgConv.getMessageCount() <= Conversation.MAX_MESSAGES);
        assertEquals(0, overMsgConv.getMessageCount(), "Oversized single tool-call block must be dropped atomically");

        // Case B: Single tool-call block with 4 x 12,000-char tool results (48,000 > MAX_CONTEXT_CHARS=32,000)
        Conversation overCharBlockConv = new Conversation("c-over-chars", "alice");
        List<ToolCall> fourCalls = new ArrayList<>();
        List<ChatMessage> fourBatch = new ArrayList<>();
        String bigChunk = "Z".repeat(12_000);
        for (int i = 0; i < 4; i++) {
            fourCalls.add(new ToolCall("big-" + i, new FunctionCall("get_portfolio", "{}")));
        }
        fourBatch.add(ChatMessage.assistant(fourCalls));
        for (int i = 0; i < 4; i++) {
            fourBatch.add(ChatMessage.tool("big-" + i, "get_portfolio", bigChunk));
        }
        overCharBlockConv.addMessages(fourBatch);
        assertTrue(overCharBlockConv.getTotalContextChars() <= Conversation.MAX_CONTEXT_CHARS);
        assertValidToolCallIntegrity(overCharBlockConv.getMessages());

        // Case C: Single tool-call block whose assistant arguments alone exceed MAX_CONTEXT_CHARS
        Conversation hugeArgsConv = new Conversation("c-huge-args", "alice");
        ToolCall hugeArgCall = new ToolCall("huge-1", new FunctionCall("get_portfolio", "A".repeat(35_000)));
        hugeArgsConv.addMessages(List.of(
                ChatMessage.assistant(List.of(hugeArgCall)),
                ChatMessage.tool("huge-1", "get_portfolio", "{}")
        ));
        assertTrue(hugeArgsConv.getTotalContextChars() <= Conversation.MAX_CONTEXT_CHARS);
        assertEquals(0, hugeArgsConv.getMessageCount());

        // Case D: Single plain message exceeding MAX_CONTEXT_CHARS is truncated safely
        Conversation hugePlainConv = new Conversation("c-huge-plain", "alice");
        hugePlainConv.addMessage(ChatMessage.assistant("M".repeat(50_000)));
        assertEquals(1, hugePlainConv.getMessageCount());
        assertTrue(hugePlainConv.getTotalContextChars() <= Conversation.MAX_CONTEXT_CHARS);

        // Case E: Broken tool-call block (extra mismatched tool result) leaves zero orphans
        Conversation brokenConv = new Conversation("c-broken", "alice");
        brokenConv.addMessage(ChatMessage.user("Hello"));
        ToolCall tc = new ToolCall("ok-1", new FunctionCall("get_wallet_balance", "{}"));
        brokenConv.addMessages(List.of(
                ChatMessage.assistant(List.of(tc)),
                ChatMessage.tool("ok-1", "get_wallet_balance", "{}"),
                ChatMessage.tool("stray-2", "get_wallet_balance", "{}")
        ));
        assertEquals(1, brokenConv.getMessageCount());
        assertEquals("user", brokenConv.getMessages().get(0).getRole());
    }

    @Test
    @DisplayName("TradingAgentService bounds tool calls per turn and total tool-result chars across a turn")
    void tradingAgentBoundsExcessiveToolCallsAndTotalToolResultSize() {
        AssistantProperties props = new AssistantProperties();
        props.setEnabled(true);
        props.setModel("llama-3.3-70b-versatile");

        AtomicInteger executions = new AtomicInteger(0);
        AssistantTool verboseTool = new AssistantTool() {
            @Override public String getName() { return "verbose_readonly_tool"; }
            @Override public String getDescription() { return "returns large payload"; }
            @Override public ToolDefinition getToolDefinition() {
                return new ToolDefinition("verbose_readonly_tool", "test", Map.of("type", "object"));
            }
            @Override public ToolResult execute(String username, String argumentsJson) {
                executions.incrementAndGet();
                return ToolResult.success("R".repeat(20_000));
            }
        };

        ToolRegistry registry = new ToolRegistry(List.of(verboseTool));
        ConversationService convService = new ConversationService();
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.isEnabled()).thenReturn(true);

        TradingAgentService agent = new TradingAgentService(llmClient, registry, convService, props);

        // 1. Excessive tool calls (> MAX_TOOL_CALLS_PER_TURN) rejected cleanly without executing tools or corrupting history
        List<ToolCall> tooManyCalls = new ArrayList<>();
        for (int i = 0; i < TradingAgentService.MAX_TOOL_CALLS_PER_TURN + 2; i++) {
            tooManyCalls.add(new ToolCall("excess-" + i, new FunctionCall("verbose_readonly_tool", "{}")));
        }
        when(llmClient.chatCompletion(any())).thenReturn(toolCallsResponse(tooManyCalls));

        AgentResponse excessResp = agent.processUserMessage("alice", "Run too many tools", null);
        assertFalse(excessResp.isSuccess());
        assertEquals("TOO_MANY_TOOL_CALLS", excessResp.getError());
        assertEquals(0, executions.get());
        Conversation excessConv = convService.getConversation(excessResp.getConversationId(), "alice");
        assertValidToolCallIntegrity(excessConv.getMessages());
        assertTrue(excessConv.getMessageCount() <= Conversation.MAX_MESSAGES);

        // 2. 6 valid tool calls each returning 20,000 chars (120,000 raw) are bounded to <= MAX_TURN_TOOL_RESULT_CHARS
        List<ToolCall> sixCalls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sixCalls.add(new ToolCall("call-" + i, new FunctionCall("verbose_readonly_tool", "{}")));
        }
        when(llmClient.chatCompletion(any()))
                .thenReturn(toolCallsResponse(sixCalls))
                .thenReturn(textCompletion("Summarized cleanly."));

        AgentResponse boundedResp = agent.processUserMessage("alice", "Fetch 6 large responses", null);
        assertTrue(boundedResp.isSuccess());
        assertEquals(6, executions.get());

        Conversation boundedConv = convService.getConversation(boundedResp.getConversationId(), "alice");
        assertValidToolCallIntegrity(boundedConv.getMessages());
        assertTrue(boundedConv.getMessageCount() <= Conversation.MAX_MESSAGES);
        assertTrue(boundedConv.getTotalContextChars() <= Conversation.MAX_CONTEXT_CHARS);

        int totalToolChars = boundedConv.getMessages().stream()
                .filter(m -> "tool".equalsIgnoreCase(m.getRole()))
                .mapToInt(m -> m.getContent() != null ? m.getContent().length() : 0)
                .sum();
        assertTrue(totalToolChars <= TradingAgentService.MAX_TURN_TOOL_RESULT_CHARS,
                "Total tool result chars in a single turn must not exceed MAX_TURN_TOOL_RESULT_CHARS, was: " + totalToolChars);
    }

    @Test
    @DisplayName("Simultaneous requests on the same conversationId serialize without interleaving and keep cross-user isolation")
    void concurrentRequestsOnSameConversationIdDoNotInterleaveAndPreserveCrossUserIsolation() throws Exception {
        AssistantProperties props = new AssistantProperties();
        props.setEnabled(true);
        props.setModel("llama-3.3-70b-versatile");

        AssistantTool readOnlyTool = new AssistantTool() {
            @Override public String getName() { return "get_wallet_balance"; }
            @Override public String getDescription() { return "read-only wallet"; }
            @Override public ToolDefinition getToolDefinition() {
                return new ToolDefinition("get_wallet_balance", "test", Map.of("type", "object"));
            }
            @Override public ToolResult execute(String username, String argumentsJson) {
                return ToolResult.success("{\"user\":\"" + username + "\",\"balance\":100000}");
            }
        };

        ToolRegistry registry = new ToolRegistry(List.of(readOnlyTool));
        ConversationService convService = new ConversationService();
        Conversation sharedConv = convService.createConversation("alice");
        String convId = sharedConv.getId();

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxConcurrentInFlight = new AtomicInteger(0);
        CountDownLatch firstTurnInsideLlm = new CountDownLatch(1);
        CountDownLatch releaseFirstTurn = new CountDownLatch(1);
        AtomicInteger callSeq = new AtomicInteger(0);

        LlmClient slowLlm = new LlmClient() {
            @Override public boolean isEnabled() { return true; }

            @Override
            public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
                int active = inFlight.incrementAndGet();
                maxConcurrentInFlight.accumulateAndGet(active, Math::max);
                try {
                    int seq = callSeq.incrementAndGet();
                    List<ChatMessage> msgs = request.getMessages();
                    ChatMessage last = msgs.get(msgs.size() - 1);

                    if (seq == 1) {
                        // Hold Turn 1 in-flight so Turn 2 and Bob's cross-user attempt arrive concurrently
                        firstTurnInsideLlm.countDown();
                        assertTrue(releaseFirstTurn.await(5, TimeUnit.SECONDS));
                    }

                    if ("user".equalsIgnoreCase(last.getRole())) {
                        String id = "tc-" + seq;
                        return toolCallsResponse(List.of(new ToolCall(id, new FunctionCall("get_wallet_balance", "{}"))));
                    }
                    // Tool result just arrived -> finish turn cleanly
                    String userPrompt = msgs.stream()
                            .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                            .reduce((first, second) -> second)
                            .map(ChatMessage::getContent)
                            .orElse("");
                    return textCompletion("Reply for: " + userPrompt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    inFlight.decrementAndGet();
                }
            }
        };

        TradingAgentService agent = new TradingAgentService(slowLlm, registry, convService, props);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<AgentResponse> req1 = pool.submit(() -> agent.processUserMessage("alice", "Turn 1", convId));
            assertTrue(firstTurnInsideLlm.await(5, TimeUnit.SECONDS), "Turn 1 must enter LLM call");

            // Launch Turn 2 for Alice simultaneously on the SAME conversationId
            Future<AgentResponse> req2 = pool.submit(() -> agent.processUserMessage("alice", "Turn 2", convId));

            // Simultaneously verify Bob is immediately rejected without blocking or mutating Alice's conversation
            Future<AgentResponse> bobAttempt = pool.submit(() -> agent.processUserMessage("bob", "Steal session", convId));
            AgentResponse bobResp = bobAttempt.get(2, TimeUnit.SECONDS);
            assertFalse(bobResp.isSuccess());
            assertEquals("ACCESS_DENIED", bobResp.getError());

            // Give Turn 2 a brief moment to contend on the per-conversation turnLock, then release Turn 1
            Thread.sleep(75);
            releaseFirstTurn.countDown();

            AgentResponse r1 = req1.get(5, TimeUnit.SECONDS);
            AgentResponse r2 = req2.get(5, TimeUnit.SECONDS);

            assertTrue(r1.isSuccess());
            assertTrue(r2.isSuccess());
            assertEquals("Reply for: Turn 1", r1.getMessage());
            assertEquals("Reply for: Turn 2", r2.getMessage());
            assertEquals(1, maxConcurrentInFlight.get(),
                    "Two concurrent requests on the same conversationId must never execute overlapping LLM turns");

            // Verify history is strictly serialized (Turn 1 complete before Turn 2 starts):
            // [user(Turn 1), assistant(tc), tool, assistant(Reply 1), user(Turn 2), assistant(tc), tool, assistant(Reply 2)]
            List<ChatMessage> history = convService.getConversation(convId, "alice").getMessages();
            assertValidToolCallIntegrity(history);
            assertEquals(8, history.size());
            assertEquals("user", history.get(0).getRole());
            assertEquals("Turn 1", history.get(0).getContent());
            assertEquals("assistant", history.get(1).getRole());
            assertNotNull(history.get(1).getToolCalls());
            assertEquals("tool", history.get(2).getRole());
            assertEquals("assistant", history.get(3).getRole());
            assertEquals("Reply for: Turn 1", history.get(3).getContent());

            assertEquals("user", history.get(4).getRole());
            assertEquals("Turn 2", history.get(4).getContent());
            assertEquals("assistant", history.get(5).getRole());
            assertNotNull(history.get(5).getToolCalls());
            assertEquals("tool", history.get(6).getRole());
            assertEquals("assistant", history.get(7).getRole());
            assertEquals("Reply for: Turn 2", history.get(7).getContent());
        } finally {
            pool.shutdownNow();
        }
    }

    private ChatCompletionResponse toolCallsResponse(List<ToolCall> calls) {
        Choice c = new Choice();
        c.setIndex(0);
        c.setFinishReason("tool_calls");
        c.setMessage(ChatMessage.assistant(calls));
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.setChoices(List.of(c));
        return resp;
    }

    private ChatCompletionResponse textCompletion(String text) {
        Choice c = new Choice();
        c.setIndex(0);
        c.setFinishReason("stop");
        c.setMessage(ChatMessage.assistant(text));
        ChatCompletionResponse resp = new ChatCompletionResponse();
        resp.setChoices(List.of(c));
        return resp;
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
