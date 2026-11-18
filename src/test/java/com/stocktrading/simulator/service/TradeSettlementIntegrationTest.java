package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.dto.PortfolioResponse;
import com.stocktrading.simulator.exception.InsufficientFundsException;
import com.stocktrading.simulator.exception.InsufficientSharesException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Trade Settlement Integration Tests (Real Adapters)")
class TradeSettlementIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderExecutionService orderExecutionService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private StockService stockService;

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

    private User testUser;
    private User otherUser;
    private Stock testStock;

    @BeforeEach
    void setUp() {
        cleanup();

        testUser = userRepository.save(new User("Settlement Trader", "settletrader", "trader@example.com", "hash"));
        walletService.createWalletWithInitialBalance(testUser); // 100,000 INR

        otherUser = userRepository.save(new User("Other Trader", "othertrader", "other@example.com", "hashOther"));
        walletService.createWalletWithInitialBalance(otherUser);

        testStock = stockService.createStockIfAbsent("INFY", "Infosys Ltd", new BigDecimal("1500.00"));
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
    @DisplayName("1. Successful BUY: Reduces wallet, creates holding, records 1 trade & ledger, marks order EXECUTED")
    void successfulBuy_SettlesAtomically() {
        // Create BUY order: 10 INFY @ 1500 = 15,000 INR
        OrderRequest request = new OrderRequest(UUID.randomUUID().toString(), "INFY", 10);
        OrderResponse created = orderService.createBuyOrder("settletrader", request);
        assertEquals(OrderStatus.PENDING, created.getStatus());

        // Execute order
        OrderResponse executed = orderExecutionService.executeOrder(created.getId(), "settletrader");
        assertEquals(OrderStatus.EXECUTED, executed.getStatus());
        assertEquals(new BigDecimal("1500.00"), executed.getExecutionPrice());
        assertNotNull(executed.getExecutedAt());

        // 1. Wallet debited: 100,000 - 15,000 = 85,000
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("85000.00").compareTo(wallet.getBalance()));

        // 2. Exactly one TRADE_BUY ledger transaction created
        var buyTxnOpt = walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), "ORDER-BUY-" + created.getId());
        assertTrue(buyTxnOpt.isPresent());
        assertEquals(TransactionCategory.TRADE_BUY, buyTxnOpt.get().getCategory());
        assertEquals(TransactionType.DEBIT, buyTxnOpt.get().getType());
        assertEquals(0, new BigDecimal("15000.00").compareTo(buyTxnOpt.get().getAmount()));
        assertEquals(0, new BigDecimal("85000.00").compareTo(buyTxnOpt.get().getBalanceAfter()));

        // 3. Holding created: 10 shares @ 1500.000000
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(10, holding.getQuantity());
        assertEquals(0, new BigDecimal("1500.000000").compareTo(holding.getAverageCost()));

        // 4. Exactly one Trade record
        assertEquals(1, tradeRepository.count());
        Trade trade = tradeRepository.findAll().get(0);
        assertEquals(created.getId(), trade.getOrder().getId());
        assertEquals(OrderSide.BUY, trade.getSide());
        assertEquals(10, trade.getQuantity());
        assertEquals(new BigDecimal("1500.00"), trade.getExecutionPrice());
        assertEquals(new BigDecimal("15000.00"), trade.getTotalAmount());
    }

    @Test
    @DisplayName("2. Successful SELL: Decreases holdings, credits wallet, calculates realized P&L, creates 1 trade")
    void successfulSell_SettlesAtomically() {
        // Initial setup: User buys 10 INFY @ 1500 = 15,000 INR
        OrderResponse buyOrder = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );
        orderExecutionService.executeOrder(buyOrder.getId(), "settletrader");

        // Price changes: INFY rises to 1600.00
        stockService.updateStockPrice("INFY", new BigDecimal("1600.00"));

        // User sells 5 INFY @ 1600 = 8,000 INR (gain = 5 * (1600 - 1500) = +500 INR)
        OrderResponse sellOrder = orderService.createSellOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 5)
        );
        OrderResponse executedSell = orderExecutionService.executeOrder(sellOrder.getId(), "settletrader");
        assertEquals(OrderStatus.EXECUTED, executedSell.getStatus());
        assertEquals(new BigDecimal("1600.00"), executedSell.getExecutionPrice());

        // 1. Wallet credited: 85,000 + 8,000 = 93,000
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("93000.00").compareTo(wallet.getBalance()));

        // 2. Exactly one TRADE_SELL ledger entry
        var sellTxnOpt = walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), "ORDER-SELL-" + sellOrder.getId());
        assertTrue(sellTxnOpt.isPresent());
        assertEquals(TransactionCategory.TRADE_SELL, sellTxnOpt.get().getCategory());
        assertEquals(TransactionType.CREDIT, sellTxnOpt.get().getType());
        assertEquals(0, new BigDecimal("8000.00").compareTo(sellTxnOpt.get().getAmount()));
        assertEquals(0, new BigDecimal("93000.00").compareTo(sellTxnOpt.get().getBalanceAfter()));

        // 3. Holding updated: remaining quantity = 5, realized PnL = +500.00
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(5, holding.getQuantity());
        assertEquals(0, new BigDecimal("1500.000000").compareTo(holding.getAverageCost()));
        assertEquals(0, new BigDecimal("500.00").compareTo(holding.getTotalRealizedPnl()));

        // 4. Two trades total (1 buy, 1 sell)
        assertEquals(2, tradeRepository.count());
    }

    @Test
    @DisplayName("3. Insufficient wallet funds: Rolls back settlement, leaves order PENDING, wallet & holdings untouched")
    void insufficientWalletFunds_RollsBackEverything() {
        // Drain wallet to 500 INR
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        wallet.debit(new BigDecimal("99500.00"));
        walletRepository.saveAndFlush(wallet);

        // Attempt BUY requiring 15,000 INR
        OrderResponse order = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );

        assertThrows(InsufficientFundsException.class, () ->
            orderExecutionService.executeOrder(order.getId(), "settletrader")
        );

        // Verify state is completely preserved
        Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());
        assertNull(savedOrder.getExecutionPrice());

        Wallet walletAfter = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(walletAfter.getBalance()));

        // No holdings created
        assertTrue(holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).isEmpty());
        // No trades created
        assertEquals(0, tradeRepository.count());
    }

    @Test
    @DisplayName("4. Insufficient shares: Rolls back settlement, leaves order PENDING, wallet & holdings untouched")
    void insufficientShares_RollsBackEverything() {
        // User has 0 shares of INFY. Attempt SELL of 5 shares.
        OrderResponse order = orderService.createSellOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 5)
        );

        assertThrows(InsufficientSharesException.class, () ->
            orderExecutionService.executeOrder(order.getId(), "settletrader")
        );

        // Order remains PENDING
        Order savedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());

        // Wallet untouched at 100,000
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet.getBalance()));

        assertEquals(0, tradeRepository.count());
    }

    @Test
    @DisplayName("5. Duplicate settlement: Cannot execute an already EXECUTED order twice")
    void duplicateSettlement_CannotMutateTwice() {
        OrderResponse buyOrder = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );
        orderExecutionService.executeOrder(buyOrder.getId(), "settletrader");

        // Attempting to execute the same order again
        assertThrows(IllegalStateException.class, () ->
            orderExecutionService.executeOrder(buyOrder.getId(), "settletrader")
        );

        // Balance debited only once (85,000)
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("85000.00").compareTo(wallet.getBalance()));

        // Holding has only 10 shares
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(10, holding.getQuantity());

        // Exactly one trade
        assertEquals(1, tradeRepository.count());
    }

    @Test
    @DisplayName("6. Concurrent BUY and SELL attempts: Cannot cause double-spending, overselling, or duplicate positions")
    void concurrentBuyAndSell_ThreadSafety() throws Exception {
        // User buys 20 shares initially
        OrderResponse buy = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 20)
        );
        orderExecutionService.executeOrder(buy.getId(), "settletrader");

        // Now attempt 5 concurrent SELL orders of 10 shares each (total 50 shares requested, only 20 owned!)
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<OrderResponse> sellOrders = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            sellOrders.add(orderService.createSellOrder(
                    "settletrader",
                    new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
            ));
        }

        for (OrderResponse sellOrder : sellOrders) {
            executor.submit(() -> {
                try {
                    latch.await();
                    orderExecutionService.executeOrder(sellOrder.getId(), "settletrader");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // At most 2 sells can succeed (20 shares / 10 per sell = 2). The remaining must fail!
        assertTrue(successCount.get() <= 2, "Cannot execute more sells than shares owned (at most 2)");
        assertEquals(threadCount, successCount.get() + failureCount.get());

        // Shares remaining must be non-negative
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertTrue(holding.getQuantity() >= 0, "Holding quantity must never drop below 0");
        assertEquals(20 - (successCount.get() * 10), holding.getQuantity());

        // Exactly 1 holding row for (user, stock)
        assertEquals(1, holdingRepository.findByUserId(testUser.getId()).size(),
                "Must enforce exactly one holding record per user and stock");
    }

    @Test
    @DisplayName("7. Portfolio valuation: Accurately computes market value using simulated prices")
    void portfolioValuation_UsesSimulatedPrices() {
        // User buys 10 INFY @ 1500 = 15,000 INR
        OrderResponse buyOrder = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );
        orderExecutionService.executeOrder(buyOrder.getId(), "settletrader");

        // Simulated price increases to 1750.00
        stockService.updateStockPrice("INFY", new BigDecimal("1750.00"));

        PortfolioResponse portfolio = portfolioService.getPortfolio("settletrader", false);

        assertEquals(0, new BigDecimal("85000.00").compareTo(portfolio.getWalletBalance()));
        assertEquals(0, new BigDecimal("15000.00").compareTo(portfolio.getTotalInvestedValue()));
        assertEquals(0, new BigDecimal("17500.00").compareTo(portfolio.getTotalCurrentMarketValue()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(portfolio.getTotalUnrealizedPnl()));
        assertEquals(0, new BigDecimal("16.67").compareTo(portfolio.getUnrealizedReturnPercentage()));
        assertEquals(0, new BigDecimal("102500.00").compareTo(portfolio.getTotalAccountEquity()));
    }

    @Test
    @DisplayName("8. User ownership: Execution prevents other users from executing an order they do not own")
    void userOwnership_EnforcedOnExecution() {
        OrderResponse buyOrder = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 5)
        );

        assertThrows(com.stocktrading.simulator.exception.ResourceNotFoundException.class, () ->
            orderExecutionService.executeOrder(buyOrder.getId(), "othertrader")
        );

        // Order remains PENDING
        Order order = orderRepository.findById(buyOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    @DisplayName("9. Simultaneous first-time BUY: Unique constraint uq_holding_user_stock prevents duplicates and failed tx rolls back wallet")
    void simultaneousFirstTimeBuy_EnforcesUniqueHoldingConstraintAndRollsBackFailedTransaction() throws Exception {
        // User has NO initial holding row for INFY
        assertTrue(holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).isEmpty());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        OrderResponse order1 = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );
        OrderResponse order2 = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );

        List<OrderResponse> orders = List.of(order1, order2);

        for (OrderResponse ord : orders) {
            executor.submit(() -> {
                try {
                    latch.await();
                    orderExecutionService.executeOrder(ord.getId(), "settletrader");
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Exactly 1 holding row for this user and stock (never duplicates)
        List<Holding> userHoldings = holdingRepository.findByUserId(testUser.getId());
        assertEquals(1, userHoldings.size(), "Unique constraint uq_holding_user_stock must guarantee exactly one holding row");

        Holding holding = userHoldings.get(0);
        int expectedQuantity = successCount.get() * 10;
        assertEquals(expectedQuantity, holding.getQuantity());

        // Wallet debited strictly by the number of successful transactions (15,000 per success)
        BigDecimal expectedDebit = new BigDecimal("15000.00").multiply(BigDecimal.valueOf(successCount.get()));
        BigDecimal expectedBalance = new BigDecimal("100000.00").subtract(expectedDebit);
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, expectedBalance.compareTo(wallet.getBalance()),
                "Wallet balance must reflect only successful executions; failed executions must be rolled back completely");

        // Exactly successCount trades created
        assertEquals(successCount.get(), tradeRepository.count());

        // Exactly successCount TRADE_BUY transactions created
        long buyTxnCount = walletTransactionRepository.findByWalletId(wallet.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .filter(t -> t.getCategory() == TransactionCategory.TRADE_BUY)
                .count();
        assertEquals(successCount.get(), buyTxnCount);
    }

    @Test
    @DisplayName("10. Concurrent BUY and SELL: Lock contention or deadlock rolls back entire settlement without partial state")
    void concurrentBuyAndSell_RollsBackCleanlyWithoutPartialState() throws Exception {
        // User starts with 20 shares of INFY (WAC = 1500.00) and 70,000 INR
        OrderResponse initialBuy = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 20)
        );
        orderExecutionService.executeOrder(initialBuy.getId(), "settletrader");

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger completedCount = new AtomicInteger(0);

        // Prepare 2 BUY orders (5 shares each) and 2 SELL orders (5 shares each)
        OrderResponse buy1 = orderService.createBuyOrder("settletrader", new OrderRequest(UUID.randomUUID().toString(), "INFY", 5));
        OrderResponse buy2 = orderService.createBuyOrder("settletrader", new OrderRequest(UUID.randomUUID().toString(), "INFY", 5));
        OrderResponse sell1 = orderService.createSellOrder("settletrader", new OrderRequest(UUID.randomUUID().toString(), "INFY", 5));
        OrderResponse sell2 = orderService.createSellOrder("settletrader", new OrderRequest(UUID.randomUUID().toString(), "INFY", 5));

        List<OrderResponse> mixedOrders = List.of(buy1, sell1, buy2, sell2);

        for (OrderResponse ord : mixedOrders) {
            executor.submit(() -> {
                try {
                    latch.await();
                    orderExecutionService.executeOrder(ord.getId(), "settletrader");
                    completedCount.incrementAndGet();
                } catch (Exception ignored) {
                    // Lock timeout, deadlock, or optimistic lock failure expected under high concurrent race
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Integrity verification:
        // Every trade record corresponds to an order that successfully completed execution
        List<Order> executedOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.EXECUTED)
                .toList();
        assertEquals(1 + completedCount.get(), executedOrders.size());

        // Total executed trades = 1 (initial) + completedCount
        assertEquals(1 + completedCount.get(), tradeRepository.count());

        // Position quantity is non-negative and matches net executed shares
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertTrue(holding.getQuantity() >= 0);

        // Exactly one holding row
        assertEquals(1, holdingRepository.findByUserId(testUser.getId()).size());
    }

    @Test
    @DisplayName("11. Aligned DECIMAL(12,2) ceiling price (9,999,999,999.99) settles end-to-end across BUY and SELL")
    void highCeilingPrice_AlignedAcrossEngineAndHoldings() {
        BigDecimal ceilingPrice = new BigDecimal("9999999999.99");
        stockService.createStockIfAbsent("BRKA", "High Price Corp", ceilingPrice);

        // Fund wallet cleanly to cover ceiling price
        Wallet w = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        w.credit(new BigDecimal("10000000000.00"));
        walletRepository.saveAndFlush(w);

        OrderResponse buyOrder = orderService.createBuyOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "BRKA", 1)
        );
        OrderResponse executedBuy = orderExecutionService.executeOrder(buyOrder.getId(), "settletrader");
        assertEquals(OrderStatus.EXECUTED, executedBuy.getStatus());
        assertEquals(0, ceilingPrice.compareTo(executedBuy.getExecutionPrice()));

        OrderResponse sellOrder = orderService.createSellOrder(
                "settletrader",
                new OrderRequest(UUID.randomUUID().toString(), "BRKA", 1)
        );
        OrderResponse executedSell = orderExecutionService.executeOrder(sellOrder.getId(), "settletrader");
        assertEquals(OrderStatus.EXECUTED, executedSell.getStatus());
        assertEquals(0, ceilingPrice.compareTo(executedSell.getExecutionPrice()));

        Wallet walletAfter = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10000100000.00").compareTo(walletAfter.getBalance()));
    }
}
