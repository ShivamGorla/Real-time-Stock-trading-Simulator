package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.service.TradeProposalService;
import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.OrderExecutionService;
import com.stocktrading.simulator.service.OrderService;
import com.stocktrading.simulator.service.StockService;
import com.stocktrading.simulator.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("AI Assistant Tools Integration Tests (Canonical StockSim Services)")
class AssistantToolsIntegrationTest {

    @Autowired private ToolRegistry toolRegistry;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private StockService stockService;
    @Autowired private OrderService orderService;
    @Autowired private OrderExecutionService orderExecutionService;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private StockPriceHistoryRepository historyRepository;
    @Autowired private StockRepository stockRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        cleanup();
        stockService.createStockIfAbsent("INFY", "Infosys Limited", new BigDecimal("1500.00"));
        stockService.createStockIfAbsent("TCS", "Tata Consultancy Services", new BigDecimal("3500.00"));

        alice = registerUser("Alice", "alice", "alice@example.com");
        bob = registerUser("Bob", "bob", "bob@example.com");

        // Alice buys 10 INFY @ 1500.00 (15,000 INR -> balance 85,000)
        OrderResponse aliceBuy = orderService.createBuyOrder("alice", new OrderRequest(UUID.randomUUID().toString(), "INFY", 10));
        orderExecutionService.executeOrder(aliceBuy.getId(), "alice");

        // Bob buys 5 TCS @ 3500.00 (17,500 INR -> balance 82,500)
        OrderResponse bobBuy = orderService.createBuyOrder("bob", new OrderRequest(UUID.randomUUID().toString(), "TCS", 5));
        orderExecutionService.executeOrder(bobBuy.getId(), "bob");
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        authTokenRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User registerUser(String name, String username, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setName(name);
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword("password123");
        req.setConfirmPassword("password123");
        userService.registerUser(req);
        return userRepository.findByUsername(username).orElseThrow();
    }

    @Test
    @DisplayName("ToolRegistry allows ONLY the 6 approved read-only tools and blocks unregistered trade execution tools")
    void toolRegistryContainsOnlySixReadOnlyTools() {
        assertThat(toolRegistry.getRegisteredToolNames()).containsExactlyInAnyOrderElementsOf(Set.of(
                "get_portfolio",
                "get_holdings",
                "get_wallet_balance",
                "get_order_history",
                "get_stock_information",
                "preview_trade_proposal"
        ));
        assertEquals(6, toolRegistry.getToolCount());

        ToolResult blocked = toolRegistry.executeTool("execute_order", "alice", "{\"symbol\":\"INFY\",\"quantity\":5}");
        assertFalse(blocked.isSuccess());
        assertThat(blocked.getContent()).contains("Unknown tool");
    }

    @Test
    @DisplayName("All read-only tools return real user-isolated data from canonical services")
    void allSixToolsWorkAgainstCanonicalServicesWithStrictUserIsolation() throws Exception {
        // 1. get_wallet_balance
        ToolResult walletRes = toolRegistry.executeTool("get_wallet_balance", "alice", "{}");
        assertTrue(walletRes.isSuccess());
        JsonNode walletJson = objectMapper.readTree(walletRes.getContent());
        assertEquals(0, new BigDecimal("85000.00").compareTo(walletJson.get("balance").decimalValue()));
        assertEquals("INR", walletJson.get("currency").asText());

        // 2. get_portfolio
        ToolResult portRes = toolRegistry.executeTool("get_portfolio", "alice", "{\"include_closed\":false}");
        assertTrue(portRes.isSuccess());
        JsonNode portJson = objectMapper.readTree(portRes.getContent());
        assertEquals(0, new BigDecimal("85000.00").compareTo(portJson.get("walletBalance").decimalValue()));
        assertEquals(1, portJson.get("holdings").size());
        assertEquals("INFY", portJson.get("holdings").get(0).get("symbol").asText());
        assertThat(portRes.getContent()).doesNotContain("TCS"); // Bob's holding never leaks

        // 3. get_holdings
        ToolResult holdingsRes = toolRegistry.executeTool("get_holdings", "alice", "{}");
        assertTrue(holdingsRes.isSuccess());
        JsonNode holdingsJson = objectMapper.readTree(holdingsRes.getContent());
        assertEquals(1, holdingsJson.size());
        assertEquals("INFY", holdingsJson.get(0).get("symbol").asText());
        assertEquals(10, holdingsJson.get(0).get("quantity").asInt());

        // 4. get_order_history (filtered + paginated + user-isolated)
        ToolResult orderRes = toolRegistry.executeTool("get_order_history", "alice", "{\"type\":\"BUY\",\"symbol\":\"INFY\",\"page\":0,\"size\":10}");
        assertTrue(orderRes.isSuccess());
        JsonNode orderJson = objectMapper.readTree(orderRes.getContent());
        assertEquals(1, orderJson.get("totalElements").asInt());
        assertEquals("INFY", orderJson.get("orders").get(0).get("symbol").asText());
        assertThat(orderRes.getContent()).doesNotContain("TCS");

        // 5. get_stock_information
        ToolResult stockRes = toolRegistry.executeTool("get_stock_information", "alice", "{\"symbol\":\"tcs\"}");
        assertTrue(stockRes.isSuccess());
        JsonNode stockJson = objectMapper.readTree(stockRes.getContent());
        assertEquals("TCS", stockJson.get("symbol").asText());
        assertEquals(0, new BigDecimal("3500.00").compareTo(stockJson.get("currentPrice").decimalValue()));
    }

    @Test
    @DisplayName("6. preview_trade_proposal calculates BUY/SELL advisories without mutating any DB table")
    void previewTradeProposalIsStrictlyReadOnlyAndNeverMutatesState() throws Exception {
        long ordersBefore = orderRepository.count();
        long tradesBefore = tradeRepository.count();
        long txnsBefore = walletTransactionRepository.count();
        BigDecimal balanceBefore = walletRepository.findByUserId(alice.getId()).orElseThrow().getBalance();
        int holdingBefore = holdingRepository.findByUserId(alice.getId()).get(0).getQuantity();

        // Executable BUY preview (5 INFY @ 1500 = 7500 <= 85000)
        ToolResult buyPreview = toolRegistry.executeTool(
                "preview_trade_proposal", "alice", "{\"symbol\":\"INFY\",\"side\":\"BUY\",\"quantity\":5}");
        assertTrue(buyPreview.isSuccess());
        JsonNode buyJson = objectMapper.readTree(buyPreview.getContent());
        assertTrue(buyJson.get("isExecutable").asBoolean());
        assertEquals(0, new BigDecimal("7500.00").compareTo(buyJson.get("estimatedTotalAmount").decimalValue()));
        assertEquals(0, new BigDecimal("77500.00").compareTo(buyJson.get("estimatedBalanceAfter").decimalValue()));
        assertEquals(TradeProposalService.ADVISORY_NOTICE, buyJson.get("advisoryNotice").asText());

        // Insufficient funds BUY preview (100 TCS @ 3500 = 350,000 > 85,000)
        ToolResult poorBuy = toolRegistry.executeTool(
                "preview_trade_proposal", "alice", "{\"symbol\":\"TCS\",\"side\":\"BUY\",\"quantity\":100}");
        assertTrue(poorBuy.isSuccess());
        assertFalse(objectMapper.readTree(poorBuy.getContent()).get("isExecutable").asBoolean());

        // Executable SELL preview (4 INFY <= 10 held)
        ToolResult sellPreview = toolRegistry.executeTool(
                "preview_trade_proposal", "alice", "{\"symbol\":\"INFY\",\"side\":\"SELL\",\"quantity\":4}");
        assertTrue(sellPreview.isSuccess());
        assertTrue(objectMapper.readTree(sellPreview.getContent()).get("isExecutable").asBoolean());

        // Insufficient shares SELL preview (25 INFY > 10 held)
        ToolResult overSell = toolRegistry.executeTool(
                "preview_trade_proposal", "alice", "{\"symbol\":\"INFY\",\"side\":\"SELL\",\"quantity\":25}");
        assertTrue(overSell.isSuccess());
        assertFalse(objectMapper.readTree(overSell.getContent()).get("isExecutable").asBoolean());

        // Verify ZERO database state mutations occurred across all 4 previews
        assertEquals(ordersBefore, orderRepository.count());
        assertEquals(tradesBefore, tradeRepository.count());
        assertEquals(txnsBefore, walletTransactionRepository.count());
        Wallet walletAfter = walletRepository.findByUserId(alice.getId()).orElseThrow();
        assertEquals(0, balanceBefore.compareTo(walletAfter.getBalance()));
        assertEquals(holdingBefore, holdingRepository.findByUserId(alice.getId()).get(0).getQuantity());
    }

    @Test
    @DisplayName("Invalid symbols, quantities, unknown parameters, and malformed JSON fail safely")
    void rejectsInvalidArgumentsAndMalformedJson() {
        assertFalse(toolRegistry.executeTool("get_stock_information", "alice", "{\"symbol\":\"NONEXISTENT\"}").isSuccess());
        assertFalse(toolRegistry.executeTool("get_stock_information", "alice", "{\"symbol\":\"INFY; DROP TABLE users\"}").isSuccess());
        assertFalse(toolRegistry.executeTool("preview_trade_proposal", "alice", "{\"symbol\":\"INFY\",\"side\":\"BUY\",\"quantity\":0}").isSuccess());
        assertFalse(toolRegistry.executeTool("preview_trade_proposal", "alice", "{\"symbol\":\"INFY\",\"side\":\"BUY\",\"quantity\":100001}").isSuccess());
        assertFalse(toolRegistry.executeTool("preview_trade_proposal", "alice", "{\"symbol\":\"INFY\",\"side\":\"SHORT\",\"quantity\":1}").isSuccess());
        // Unknown parameter injection (e.g., attempting to pass userId / username to spoof Bob)
        assertFalse(toolRegistry.executeTool("get_wallet_balance", "alice", "{\"username\":\"bob\"}").isSuccess());
        assertFalse(toolRegistry.executeTool("get_portfolio", "alice", "{not valid json").isSuccess());
    }

    @Test
    @DisplayName("get_order_history handles DB pagination, filtering, int-overflow page*size, and Integer.MAX_VALUE pages safely")
    void orderHistoryPaginationHandlesIntegerOverflowAndLargePagesSafely() throws Exception {
        // Alice already has 1 BUY INFY order from setUp; add 2 more orders (1 BUY TCS, 1 SELL INFY)
        OrderResponse buyTcs = orderService.createBuyOrder("alice", new OrderRequest(UUID.randomUUID().toString(), "TCS", 2));
        orderExecutionService.executeOrder(buyTcs.getId(), "alice");
        OrderResponse sellInfy = orderService.createSellOrder("alice", new OrderRequest(UUID.randomUUID().toString(), "INFY", 3));
        orderExecutionService.executeOrder(sellInfy.getId(), "alice");

        // Page 0, size 2 -> returns 2 of Alice's 3 orders (totalPages = 2)
        ToolResult page0 = toolRegistry.executeTool("get_order_history", "alice", "{\"page\":0,\"size\":2}");
        assertTrue(page0.isSuccess());
        JsonNode p0Json = objectMapper.readTree(page0.getContent());
        assertEquals(2, p0Json.get("orders").size());
        assertEquals(3, p0Json.get("totalElements").asLong());
        assertEquals(2, p0Json.get("totalPages").asInt());

        // Page 1, size 2 -> returns remaining 1 order
        ToolResult page1 = toolRegistry.executeTool("get_order_history", "alice", "{\"page\":1,\"size\":2}");
        assertTrue(page1.isSuccess());
        JsonNode p1Json = objectMapper.readTree(page1.getContent());
        assertEquals(1, p1Json.get("orders").size());
        assertEquals(3, p1Json.get("totalElements").asLong());

        // DB-level filtering by side + symbol (case-insensitive)
        ToolResult filtered = toolRegistry.executeTool(
                "get_order_history", "alice", "{\"type\":\"SELL\",\"symbol\":\"infy\",\"page\":0,\"size\":10}");
        assertTrue(filtered.isSuccess());
        JsonNode filteredJson = objectMapper.readTree(filtered.getContent());
        assertEquals(1, filteredJson.get("totalElements").asLong());
        assertEquals("SELL", filteredJson.get("orders").get(0).get("side").asText());
        assertEquals("INFY", filteredJson.get("orders").get(0).get("symbol").asText());

        // 32-bit int overflow boundary: 107_374_183 * 20 would overflow int to -2_147_483_636
        ToolResult overflowPage = toolRegistry.executeTool(
                "get_order_history", "alice", "{\"page\":107374183,\"size\":20}");
        assertTrue(overflowPage.isSuccess(), "Overflow page*size must not fail or throw IndexOutOfBoundsException");
        JsonNode overflowJson = objectMapper.readTree(overflowPage.getContent());
        assertEquals(0, overflowJson.get("orders").size());
        assertEquals(107374183, overflowJson.get("page").asInt());
        assertEquals(20, overflowJson.get("size").asInt());
        assertEquals(3, overflowJson.get("totalElements").asLong());
        assertEquals(1, overflowJson.get("totalPages").asInt());

        // Extreme page = Integer.MAX_VALUE with oversized size (clamped to MAX_PAGE_SIZE=50)
        ToolResult maxPage = toolRegistry.executeTool(
                "get_order_history", "alice", "{\"page\":2147483647,\"size\":999}");
        assertTrue(maxPage.isSuccess());
        JsonNode maxJson = objectMapper.readTree(maxPage.getContent());
        assertEquals(0, maxJson.get("orders").size());
        assertEquals(Integer.MAX_VALUE, maxJson.get("page").asInt());
        assertEquals(OrderTools.MAX_PAGE_SIZE, maxJson.get("size").asInt());
        assertEquals(3, maxJson.get("totalElements").asLong());
        assertEquals(1, maxJson.get("totalPages").asInt());

        // Invalid page / size bounds return controlled tool errors
        assertFalse(toolRegistry.executeTool("get_order_history", "alice", "{\"page\":-1,\"size\":10}").isSuccess());
        assertFalse(toolRegistry.executeTool("get_order_history", "alice", "{\"page\":0,\"size\":0}").isSuccess());
    }
}
