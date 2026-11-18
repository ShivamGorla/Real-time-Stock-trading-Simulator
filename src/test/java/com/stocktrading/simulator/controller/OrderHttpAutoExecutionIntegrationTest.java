package com.stocktrading.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.PortfolioResponse;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.PortfolioService;
import com.stocktrading.simulator.service.StockService;
import com.stocktrading.simulator.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end HTTP integration test validating automatic order execution (stock.order.auto-execute=true)
 * with REAL database-backed JpaWalletAdapter and JpaHoldingsAdapter beans.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"stock.order.auto-execute=true"})
@DisplayName("Order HTTP Auto-Execution Integration Tests (Real Adapters)")
class OrderHttpAutoExecutionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private PortfolioService portfolioService;

    private User testUser;
    private Stock testStock;

    @BeforeEach
    void setUp() {
        cleanup();

        testUser = userRepository.save(new User("Http Trader", "httptrader", "http@example.com", "hashpass"));
        walletService.createWalletWithInitialBalance(testUser); // 100,000 INR

        testStock = stockService.createStockIfAbsent("INFY", "Infosys Limited", new BigDecimal("1500.00"));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "httptrader")
    @DisplayName("1. POST /api/orders/buy auto-executes: debits wallet, credits holding, logs TRADE_BUY and Trade")
    void httpBuyOrder_AutoExecutesCompletely() throws Exception {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "INFY", 10); // 10 * 1500 = 15,000 INR

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientOrderId").value(clientOrderId))
                .andExpect(jsonPath("$.username").value("httptrader"))
                .andExpect(jsonPath("$.symbol").value("INFY"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.quotedPrice").value(1500.00))
                .andExpect(jsonPath("$.executionPrice").value(1500.00))
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.executedAt").exists());

        // 1. Wallet balance decreased: 100,000 - 15,000 = 85,000
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("85000.00").compareTo(wallet.getBalance()));

        // 2. Holdings increased to 10 shares, WAC = 1500.000000
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(10, holding.getQuantity());
        assertEquals(0, new BigDecimal("1500.000000").compareTo(holding.getAverageCost()));

        // 3. Exactly one TRADE_BUY wallet transaction
        List<WalletTransaction> txns = walletTransactionRepository.findByWalletId(wallet.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(2, txns.size()); // 1 INITIAL_BALANCE + 1 TRADE_BUY
        WalletTransaction buyTxn = txns.stream()
                .filter(t -> t.getCategory() == TransactionCategory.TRADE_BUY)
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("15000.00").compareTo(buyTxn.getAmount()));
        assertEquals(TransactionType.DEBIT, buyTxn.getType());
        assertEquals(0, new BigDecimal("85000.00").compareTo(buyTxn.getBalanceAfter()));

        // 4. Exactly one Trade audit record
        List<Trade> trades = tradeRepository.findAll();
        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(OrderSide.BUY, trade.getSide());
        assertEquals(10, trade.getQuantity());
        assertEquals(0, new BigDecimal("1500.00").compareTo(trade.getExecutionPrice()));
        assertEquals(0, new BigDecimal("15000.00").compareTo(trade.getTotalAmount()));
    }

    @Test
    @WithMockUser(username = "httptrader")
    @DisplayName("2. POST /api/orders/sell completes reverse flow with realized P&L from execution price")
    void httpSellOrder_AutoExecutesReverseFlowWithRealizedPnl() throws Exception {
        // Initial BUY of 10 shares @ 1500.00
        String buyId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderRequest(buyId, "INFY", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        // Price updates to 1800.00
        stockService.updateStockPrice("INFY", new BigDecimal("1800.00"));

        // SELL 4 shares @ 1800.00 -> Gross credit = 4 * 1800 = 7,200.00 INR
        // Realized P&L = 4 * (1800 - 1500) = +1,200.00 INR
        String sellId = UUID.randomUUID().toString();
        OrderRequest sellRequest = new OrderRequest(sellId, "INFY", 4);

        mockMvc.perform(post("/api/orders/sell")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientOrderId").value(sellId))
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.quantity").value(4))
                .andExpect(jsonPath("$.executionPrice").value(1800.00))
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        // 1. Wallet balance: 85,000 + 7,200 = 92,200 INR
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("92200.00").compareTo(wallet.getBalance()));

        // 2. Holding quantity: 10 - 4 = 6 shares, WAC remains 1500.000000, realized P&L = 1200.00
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(6, holding.getQuantity());
        assertEquals(0, new BigDecimal("1500.000000").compareTo(holding.getAverageCost()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(holding.getTotalRealizedPnl()));

        // 3. Exactly one TRADE_SELL wallet transaction
        List<WalletTransaction> txns = walletTransactionRepository.findByWalletId(wallet.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(3, txns.size()); // INIT + BUY + SELL
        WalletTransaction sellTxn = txns.stream()
                .filter(t -> t.getCategory() == TransactionCategory.TRADE_SELL)
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("7200.00").compareTo(sellTxn.getAmount()));
        assertEquals(TransactionType.CREDIT, sellTxn.getType());
        assertEquals(0, new BigDecimal("92200.00").compareTo(sellTxn.getBalanceAfter()));

        // 4. Two trade records in total
        assertEquals(2, tradeRepository.count());

        // 5. Portfolio valuation check
        PortfolioResponse portfolio = portfolioService.getPortfolio("httptrader", false);
        assertEquals(0, new BigDecimal("92200.00").compareTo(portfolio.getWalletBalance()));
        assertEquals(0, new BigDecimal("9000.00").compareTo(portfolio.getTotalInvestedValue())); // 6 * 1500
        assertEquals(0, new BigDecimal("10800.00").compareTo(portfolio.getTotalCurrentMarketValue())); // 6 * 1800
        assertEquals(0, new BigDecimal("1800.00").compareTo(portfolio.getTotalUnrealizedPnl()));
        assertEquals(0, new BigDecimal("1200.00").compareTo(portfolio.getTotalRealizedPnl()));
        assertEquals(0, new BigDecimal("103000.00").compareTo(portfolio.getTotalAccountEquity()));
    }

    @Test
    @WithMockUser(username = "httptrader")
    @DisplayName("3. Insufficient funds on BUY: Returns 422, leaves committed order PENDING, rolls back settlement")
    void httpBuyOrder_InsufficientFunds_LeavesOrderPendingAndRollsBackSettlement() throws Exception {
        // Stock price 1500.00, user has 100,000 INR. Request 100 shares = 150,000 INR (exceeds balance)
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "INFY", 100);

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("Insufficient funds in wallet")));

        // Phase 1 committed order exists and remains PENDING
        Order order = orderRepository.findByUserAndClientOrderId(testUser, clientOrderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertNull(order.getExecutionPrice());
        assertNull(order.getExecutedAt());

        // Wallet balance remains 100,000
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet.getBalance()));

        // No holdings created
        assertTrue(holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).isEmpty());

        // No trades created
        assertEquals(0, tradeRepository.count());

        // No TRADE_BUY transaction created (only INITIAL_BALANCE exists)
        List<WalletTransaction> txns = walletTransactionRepository.findByWalletId(wallet.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(1, txns.size());
        assertEquals(TransactionCategory.INITIAL_BALANCE, txns.get(0).getCategory());
    }

    @Test
    @WithMockUser(username = "httptrader")
    @DisplayName("4. Insufficient shares on SELL: Returns 422, leaves committed order PENDING, rolls back settlement")
    void httpSellOrder_InsufficientShares_LeavesOrderPendingAndRollsBackSettlement() throws Exception {
        // User owns 0 shares of INFY. Attempt SELL of 5 shares.
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "INFY", 5);

        mockMvc.perform(post("/api/orders/sell")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", containsString("No shares held for stock")));

        // Phase 1 committed order exists and remains PENDING
        Order order = orderRepository.findByUserAndClientOrderId(testUser, clientOrderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertNull(order.getExecutionPrice());
        assertNull(order.getExecutedAt());

        // Wallet balance remains 100,000
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet.getBalance()));

        // No trades created
        assertEquals(0, tradeRepository.count());

        // No TRADE_SELL transaction created
        List<WalletTransaction> txns = walletTransactionRepository.findByWalletId(wallet.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(1, txns.size());
        assertEquals(TransactionCategory.INITIAL_BALANCE, txns.get(0).getCategory());
    }
}
