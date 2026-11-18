package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.exception.InsufficientFundsException;
import com.stocktrading.simulator.exception.InsufficientSharesException;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.port.HoldingsPort;
import com.stocktrading.simulator.port.WalletPort;
import com.stocktrading.simulator.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderExecutionServiceTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    @MockBean
    private WalletPort walletPort;

    @MockBean
    private HoldingsPort holdingsPort;

    @Autowired
    private OrderExecutionService orderExecutionService;

    private User testUser1;
    private User testUser2;
    private Stock testStock;

    @org.junit.jupiter.api.AfterEach
    @BeforeEach
    void setup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        holdingRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();

        reset(walletPort, holdingsPort);

        testUser1 = userRepository.save(new User("User One", "userone", "one@example.com", "hash1"));
        testUser2 = userRepository.save(new User("User Two", "usertwo", "two@example.com", "hash2"));
        testStock = stockService.createStockIfAbsent("INFY", "Infosys Ltd", new BigDecimal("1500.00"));
    }

    @Test
    void executeOrder_BuyOrder_SuccessWithMockedAdapters() {
        // Create a BUY order (initial quoted price 1500.00)
        OrderResponse createdOrder = orderService.createBuyOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );

        // Simulate a price movement before execution: 1500.00 -> 1550.00
        stockService.updateStockPrice("INFY", new BigDecimal("1550.00"), Instant.now());

        OrderResponse executed = orderExecutionService.executeOrder(createdOrder.getId(), "userone");

        assertEquals(OrderStatus.EXECUTED, executed.getStatus());
        assertEquals(new BigDecimal("1550.00"), executed.getExecutionPrice());
        assertEquals(new BigDecimal("1500.00"), executed.getQuotedPrice()); // Preserved quoted price
        assertNotNull(executed.getExecutedAt());

        // Verify wallet debited at current price * quantity (1550 * 10 = 15500.00)
        verify(walletPort, times(1)).debitSufficientFunds(
                eq(testUser1.getId()),
                eq(new BigDecimal("15500.00")),
                eq(createdOrder.getId()),
                anyString()
        );

        // Verify holdings added
        verify(holdingsPort, times(1)).addShares(
                eq(testUser1.getId()),
                eq("INFY"),
                eq(10),
                eq(new BigDecimal("1550.00")),
                eq(createdOrder.getId())
        );

        // Verify Trade audit record persisted
        assertEquals(1, tradeRepository.count());
        Trade trade = tradeRepository.findAll().get(0);
        assertEquals(createdOrder.getId(), trade.getOrder().getId());
        assertEquals(testUser1.getId(), trade.getUser().getId());
        assertEquals(new BigDecimal("1550.00"), trade.getExecutionPrice());
        assertEquals(new BigDecimal("15500.00"), trade.getTotalAmount());
        assertEquals(OrderSide.BUY, trade.getSide());
        assertEquals(10, trade.getQuantity());
    }

    @Test
    void executeOrder_SellOrder_SuccessWithMockedAdapters() {
        // Create a SELL order
        OrderResponse createdOrder = orderService.createSellOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 5)
        );

        OrderResponse executed = orderExecutionService.executeOrder(createdOrder.getId(), "userone");

        assertEquals(OrderStatus.EXECUTED, executed.getStatus());
        assertEquals(new BigDecimal("1500.00"), executed.getExecutionPrice());
        assertNotNull(executed.getExecutedAt());

        // Verify shares deducted
        verify(holdingsPort, times(1)).deductAvailableShares(
                eq(testUser1.getId()),
                eq("INFY"),
                eq(5),
                eq(new BigDecimal("1500.00")),
                eq(createdOrder.getId())
        );

        // Verify wallet credited at 1500 * 5 = 7500.00
        verify(walletPort, times(1)).creditFunds(
                eq(testUser1.getId()),
                eq(new BigDecimal("7500.00")),
                eq(createdOrder.getId()),
                anyString()
        );

        // Verify Trade audit record
        assertEquals(1, tradeRepository.count());
        Trade trade = tradeRepository.findAll().get(0);
        assertEquals(OrderSide.SELL, trade.getSide());
        assertEquals(new BigDecimal("7500.00"), trade.getTotalAmount());
    }

    @Test
    void executeOrder_InsufficientFunds_ThrowsException_LeavesOrderPending() {
        doThrow(new InsufficientFundsException("Insufficient cash balance"))
                .when(walletPort).debitSufficientFunds(anyLong(), any(BigDecimal.class), anyLong(), anyString());

        OrderResponse created = orderService.createBuyOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );

        assertThrows(InsufficientFundsException.class, () ->
                orderExecutionService.executeOrder(created.getId(), "userone")
        );

        // Verify order remained PENDING
        Order order = orderRepository.findById(created.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());

        // Verify no shares added and no trade recorded
        verify(holdingsPort, never()).addShares(anyLong(), anyString(), anyInt(), any(BigDecimal.class), anyLong());
        assertEquals(0, tradeRepository.count());
    }

    @Test
    void executeOrder_InsufficientShares_ThrowsException_LeavesOrderPending() {
        doThrow(new InsufficientSharesException("Insufficient shares owned"))
                .when(holdingsPort).deductAvailableShares(anyLong(), anyString(), anyInt(), any(BigDecimal.class), anyLong());

        OrderResponse created = orderService.createSellOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 20)
        );

        assertThrows(InsufficientSharesException.class, () ->
                orderExecutionService.executeOrder(created.getId(), "userone")
        );

        // Verify order remained PENDING
        Order order = orderRepository.findById(created.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());

        // Verify no wallet credited and no trade recorded
        verify(walletPort, never()).creditFunds(anyLong(), any(BigDecimal.class), anyLong(), anyString());
        assertEquals(0, tradeRepository.count());
    }

    @Test
    void executeOrder_WhenAlreadyExecuted_ThrowsIllegalStateException() {
        OrderResponse created = orderService.createBuyOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 5)
        );

        // First execution succeeds
        orderExecutionService.executeOrder(created.getId(), "userone");

        // Second execution attempt must fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                orderExecutionService.executeOrder(created.getId(), "userone")
        );
        assertTrue(ex.getMessage().contains("not in PENDING state"));

        // Verify only 1 trade recorded
        assertEquals(1, tradeRepository.count());
    }

    @Test
    void executeOrder_CrossUserOwnership_ThrowsResourceNotFoundException() {
        OrderResponse createdByUserOne = orderService.createBuyOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 5)
        );

        // User two attempts to execute user one's order -> 404 / ResourceNotFound
        assertThrows(ResourceNotFoundException.class, () ->
                orderExecutionService.executeOrder(createdByUserOne.getId(), "usertwo")
        );

        // Verify no port calls
        verifyNoInteractions(walletPort, holdingsPort);
    }

    @Test
    void trade_UniqueConstraintOnOrderId_PreventsDuplicateTrades() {
        Order order = orderRepository.save(new Order(
                UUID.randomUUID().toString(),
                testUser1,
                testStock,
                OrderSide.BUY,
                5,
                new BigDecimal("1500.00"),
                new BigDecimal("7500.00"),
                Instant.now()
        ));

        Trade trade1 = new Trade(order, testUser1, testStock, OrderSide.BUY, 5, new BigDecimal("1500.00"), new BigDecimal("7500.00"), Instant.now());
        tradeRepository.saveAndFlush(trade1);

        Trade trade2 = new Trade(order, testUser1, testStock, OrderSide.BUY, 5, new BigDecimal("1500.00"), new BigDecimal("7500.00"), Instant.now());
        assertThrows(DataIntegrityViolationException.class, () -> {
            tradeRepository.saveAndFlush(trade2);
        });
    }

    @Test
    void executeOrder_WhenPriceExceedsMaxPrecision_ThrowsIllegalStateException_BeforeWalletOrHoldingsMutations() {
        OrderResponse created = orderService.createBuyOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 5)
        );

        Stock mockStock = mock(Stock.class);
        when(mockStock.getId()).thenReturn(testStock.getId());
        when(mockStock.getSymbol()).thenReturn("INFY");
        when(mockStock.getCurrentPrice()).thenReturn(new BigDecimal("10000000000.00")); // exceeds DECIMAL(12,2)

        StockRepository mockStockRepo = mock(StockRepository.class);
        when(mockStockRepo.findById(testStock.getId())).thenReturn(Optional.of(mockStock));

        OrderExecutionService testExecutionService = new OrderExecutionServiceImpl(
                orderRepository,
                mockStockRepo,
                tradeRepository,
                Optional.of(walletPort),
                Optional.of(holdingsPort)
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                testExecutionService.executeOrder(created.getId(), "userone")
        );
        assertTrue(ex.getMessage().contains("exceeds maximum allowed limit"));

        // Validation happened BEFORE mutations
        verifyNoInteractions(walletPort, holdingsPort);

        // Order quoted price and status remain unchanged
        Order order = orderRepository.findById(created.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("1500.00"), order.getQuotedPrice());
        assertNull(order.getExecutionPrice());
    }

    @Test
    void executeOrder_WhenTotalAmountExceedsMaxPrecision_ThrowsIllegalStateException_BeforeMutations() {
        OrderResponse created = orderService.createBuyOrder(
                "userone",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 50000)
        );

        // Set stock price so total execution amount exceeds DECIMAL(16,2) limit (99999999999999.99)
        testStock.updatePrice(new BigDecimal("3000000000.00"), Instant.now());
        stockRepository.save(testStock);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                orderExecutionService.executeOrder(created.getId(), "userone")
        );
        assertTrue(ex.getMessage().contains("exceeds maximum allowed monetary limit"));

        verifyNoInteractions(walletPort, holdingsPort);
        assertEquals(0, tradeRepository.count());
    }
}
