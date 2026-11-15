package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.exception.DuplicateOrderException;
import com.stocktrading.simulator.exception.InsufficientFundsException;
import com.stocktrading.simulator.exception.InsufficientSharesException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.port.HoldingsPort;
import com.stocktrading.simulator.port.WalletPort;
import com.stocktrading.simulator.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"stock.order.auto-execute=true"})
class OrderAutoExecutionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockService stockService;

    @MockBean
    private WalletPort walletPort;

    @MockBean
    private HoldingsPort holdingsPort;

    private User testUser;
    private Stock testStock;

    @BeforeEach
    void setup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();

        reset(walletPort, holdingsPort);

        testUser = userRepository.save(new User("Auto Trader", "autotrader", "auto@example.com", "hash"));
        testStock = stockService.createStockIfAbsent("TCS", "Tata Consultancy Services Ltd", new BigDecimal("3500.00"));
    }

    @Test
    void createBuyOrder_WhenAutoExecuteEnabledAndAdaptersPresent_TransitionsToExecuted() {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "TCS", 10);

        OrderResponse response = orderService.createBuyOrder("autotrader", request);

        // Verify order response reflects immediate execution
        assertEquals(OrderStatus.EXECUTED, response.getStatus());
        assertEquals(new BigDecimal("3500.00"), response.getExecutionPrice());
        assertEquals(new BigDecimal("3500.00"), response.getQuotedPrice());
        assertNotNull(response.getExecutedAt());

        // Verify wallet debited: 3500.00 * 10 = 35000.00
        verify(walletPort, times(1)).debitSufficientFunds(
                eq(testUser.getId()),
                eq(new BigDecimal("35000.00")),
                eq(response.getId()),
                anyString()
        );

        // Verify holdings credited
        verify(holdingsPort, times(1)).addShares(
                eq(testUser.getId()),
                eq("TCS"),
                eq(10),
                eq(response.getId())
        );

        // Verify trade persisted
        assertEquals(1, tradeRepository.count());
        Trade trade = tradeRepository.findAll().get(0);
        assertEquals(response.getId(), trade.getOrder().getId());
        assertEquals(new BigDecimal("35000.00"), trade.getTotalAmount());
        assertEquals(OrderSide.BUY, trade.getSide());
    }

    @Test
    void createSellOrder_WhenAutoExecuteEnabledAndAdaptersPresent_TransitionsToExecuted() {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "TCS", 5);

        OrderResponse response = orderService.createSellOrder("autotrader", request);

        assertEquals(OrderStatus.EXECUTED, response.getStatus());
        assertEquals(new BigDecimal("3500.00"), response.getExecutionPrice());
        assertNotNull(response.getExecutedAt());

        // Verify shares deducted
        verify(holdingsPort, times(1)).deductAvailableShares(
                eq(testUser.getId()),
                eq("TCS"),
                eq(5),
                eq(response.getId())
        );

        // Verify wallet credited: 3500.00 * 5 = 17500.00
        verify(walletPort, times(1)).creditFunds(
                eq(testUser.getId()),
                eq(new BigDecimal("17500.00")),
                eq(response.getId()),
                anyString()
        );

        assertEquals(1, tradeRepository.count());
    }

    @Test
    void createBuyOrder_WhenInsufficientFunds_ThrowsExceptionAndRollsBackSettlement() {
        doThrow(new InsufficientFundsException("Insufficient wallet cash balance"))
                .when(walletPort).debitSufficientFunds(anyLong(), any(BigDecimal.class), anyLong(), anyString());

        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "TCS", 100);

        // Throws InsufficientFundsException directly to caller (HTTP 422)
        assertThrows(InsufficientFundsException.class, () ->
                orderService.createBuyOrder("autotrader", request)
        );

        // Verify order remained in database as PENDING (from Phase 1 commit)
        Order savedOrder = orderRepository.findByUserAndClientOrderId(testUser, clientOrderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());
        assertNull(savedOrder.getExecutionPrice());
        assertNull(savedOrder.getExecutedAt());

        // Verify no shares were added and zero trades created
        verify(holdingsPort, never()).addShares(anyLong(), anyString(), anyInt(), anyLong());
        assertEquals(0, tradeRepository.count());
    }

    @Test
    void createSellOrder_WhenInsufficientShares_ThrowsExceptionAndRollsBackSettlement() {
        doThrow(new InsufficientSharesException("Insufficient shares owned"))
                .when(holdingsPort).deductAvailableShares(anyLong(), anyString(), anyInt(), anyLong());

        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "TCS", 50);

        assertThrows(InsufficientSharesException.class, () ->
                orderService.createSellOrder("autotrader", request)
        );

        Order savedOrder = orderRepository.findByUserAndClientOrderId(testUser, clientOrderId).orElseThrow();
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());

        verify(walletPort, never()).creditFunds(anyLong(), any(BigDecimal.class), anyLong(), anyString());
        assertEquals(0, tradeRepository.count());
    }

    @Test
    void createOrder_IdempotencyUnderAutoExecution_PreventsDuplicateSettlement() {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request1 = new OrderRequest(clientOrderId, "TCS", 2);

        // First submission succeeds and executes
        OrderResponse response1 = orderService.createBuyOrder("autotrader", request1);
        assertEquals(OrderStatus.EXECUTED, response1.getStatus());

        // Duplicate submission with the same clientOrderId
        OrderRequest request2 = new OrderRequest(clientOrderId, "TCS", 2);
        assertThrows(DuplicateOrderException.class, () ->
                orderService.createBuyOrder("autotrader", request2)
        );

        // Verify wallet debited only ONCE
        verify(walletPort, times(1)).debitSufficientFunds(anyLong(), any(BigDecimal.class), anyLong(), anyString());
        verify(holdingsPort, times(1)).addShares(anyLong(), anyString(), anyInt(), anyLong());

        // Exactly one trade created
        assertEquals(1, tradeRepository.count());
    }
}
