package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.exception.DuplicateOrderException;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.StockPriceHistoryRepository;
import com.stocktrading.simulator.repository.StockRepository;
import com.stocktrading.simulator.repository.TradeRepository;
import com.stocktrading.simulator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OrderExecutionService orderExecutionService;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.stocktrading.simulator.repository.WalletRepository walletRepository;

    @Autowired
    private com.stocktrading.simulator.repository.WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private com.stocktrading.simulator.repository.HoldingRepository holdingRepository;

    @Autowired
    private StockService stockService;

    private User testUser1;
    private User testUser2;
    private Stock testStock;

    @org.junit.jupiter.api.AfterEach
    @BeforeEach
    void setup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        testUser1 = userRepository.save(new User("User One", "userone", "one@example.com", "hash1"));
        testUser2 = userRepository.save(new User("User Two", "usertwo", "two@example.com", "hash2"));

        testStock = stockService.createStockIfAbsent("RELIANCE", "Reliance Industries Ltd", new BigDecimal("2500.00"));
    }

    @Test
    void createBuyOrder_ValidRequest_CreatesPendingOrderWithServerPrice() {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "RELIANCE", 10);

        OrderResponse response = orderService.createBuyOrder("userone", request);

        assertNotNull(response.getId());
        assertEquals(clientOrderId, response.getClientOrderId());
        assertEquals("userone", response.getUsername());
        assertEquals("RELIANCE", response.getSymbol());
        assertEquals(OrderSide.BUY, response.getSide());
        assertEquals(10, response.getQuantity());
        assertEquals(new BigDecimal("2500.00"), response.getQuotedPrice());
        assertEquals(new BigDecimal("25000.00"), response.getTotalQuotedAmount());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertNull(response.getExecutionPrice());
        assertNull(response.getExecutedAt());
        assertNotNull(response.getCreatedAt());
        assertTrue(response.isSimulated());
        assertEquals("INR", response.getCurrency());
    }

    @Test
    void createSellOrder_ValidRequest_CreatesPendingSellOrder() {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "RELIANCE", 5);

        OrderResponse response = orderService.createSellOrder("userone", request);

        assertEquals(OrderSide.SELL, response.getSide());
        assertEquals(5, response.getQuantity());
        assertEquals(new BigDecimal("12500.00"), response.getTotalQuotedAmount());
        assertEquals(OrderStatus.PENDING, response.getStatus());
    }

    @Test
    void createOrder_DuplicateClientOrderId_ThrowsDuplicateOrderException() {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request1 = new OrderRequest(clientOrderId, "RELIANCE", 2);
        OrderRequest request2 = new OrderRequest(clientOrderId, "RELIANCE", 4);

        orderService.createBuyOrder("userone", request1);

        assertThrows(DuplicateOrderException.class, () ->
                orderService.createBuyOrder("userone", request2));
    }

    @Test
    void createOrder_ConcurrentDuplicateConstraintViolation_ThrowsDuplicateOrderException() {
        OrderRepository mockOrderRepo = mock(OrderRepository.class);
        when(mockOrderRepo.existsByUserAndClientOrderId(any(), any())).thenReturn(false);
        when(mockOrderRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry '1-uuid' for key 'orders.uk_user_client_order_id']"
        ));

        OrderService customOrderService = new OrderService(
                mockOrderRepo,
                userRepository,
                stockRepository,
                orderExecutionService,
                transactionManager
        );

        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "RELIANCE", 10);

        DuplicateOrderException ex = assertThrows(DuplicateOrderException.class, () ->
                customOrderService.createBuyOrder("userone", request)
        );
        assertTrue(ex.getMessage().contains("concurrently submitted"));
    }

    @Test
    void createOrder_UnrelatedDatabaseConstraintViolation_RethrowsDataIntegrityViolationException() {
        OrderRepository mockOrderRepo = mock(OrderRepository.class);
        when(mockOrderRepo.existsByUserAndClientOrderId(any(), any())).thenReturn(false);
        when(mockOrderRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [Referential integrity constraint violation: \"FK_ORDERS_USER\"]"
        ));

        OrderService customOrderService = new OrderService(
                mockOrderRepo,
                userRepository,
                stockRepository,
                orderExecutionService,
                transactionManager
        );

        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "RELIANCE", 10);

        // Must rethrow DataIntegrityViolationException, NOT DuplicateOrderException
        assertThrows(DataIntegrityViolationException.class, () ->
                customOrderService.createBuyOrder("userone", request)
        );
    }

    @Test
    void createOrder_UnrelatedUniqueConstraintViolation_RethrowsDataIntegrityViolationException() {
        OrderRepository mockOrderRepo = mock(OrderRepository.class);
        when(mockOrderRepo.existsByUserAndClientOrderId(any(), any())).thenReturn(false);
        // Unrelated unique constraint (e.g. users.uk_users_username or trades.uk_trades_order_id)
        when(mockOrderRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry '1' for key 'trades.uk_trades_order_id']"
        ));

        OrderService customOrderService = new OrderService(
                mockOrderRepo,
                userRepository,
                stockRepository,
                orderExecutionService,
                transactionManager
        );

        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "RELIANCE", 10);

        // Unrelated unique constraint must rethrow DataIntegrityViolationException, NOT DuplicateOrderException
        assertThrows(DataIntegrityViolationException.class, () ->
                customOrderService.createBuyOrder("userone", request)
        );
        assertEquals(0, orderRepository.count());
        assertEquals(0, tradeRepository.count());
    }

    @Test
    void createOrder_InvalidQuantity_ThrowsIllegalArgumentException() {
        OrderRequest zeroQty = new OrderRequest(UUID.randomUUID().toString(), "RELIANCE", 0);
        assertThrows(IllegalArgumentException.class, () ->
                orderService.createBuyOrder("userone", zeroQty));

        OrderRequest negativeQty = new OrderRequest(UUID.randomUUID().toString(), "RELIANCE", -5);
        assertThrows(IllegalArgumentException.class, () ->
                orderService.createBuyOrder("userone", negativeQty));

        OrderRequest excessiveQty = new OrderRequest(UUID.randomUUID().toString(), "RELIANCE", 100001);
        assertThrows(IllegalArgumentException.class, () ->
                orderService.createBuyOrder("userone", excessiveQty));
    }

    @Test
    void createOrder_UnknownSymbol_ThrowsResourceNotFoundException() {
        OrderRequest unknown = new OrderRequest(UUID.randomUUID().toString(), "UNKNOWN", 5);
        assertThrows(ResourceNotFoundException.class, () ->
                orderService.createBuyOrder("userone", unknown));
    }

    @Test
    void getUserOrders_ReturnsOnlyUserOrdersInChronologicalReverse() {
        orderService.createBuyOrder("userone", new OrderRequest(UUID.randomUUID().toString(), "RELIANCE", 1));
        orderService.createBuyOrder("userone", new OrderRequest(UUID.randomUUID().toString(), "RELIANCE", 2));
        orderService.createBuyOrder("usertwo", new OrderRequest(UUID.randomUUID().toString(), "RELIANCE", 3));

        List<OrderResponse> userOneOrders = orderService.getUserOrders("userone");
        assertEquals(2, userOneOrders.size());
        assertEquals(2, userOneOrders.get(0).getQuantity()); // latest first
        assertEquals(1, userOneOrders.get(1).getQuantity());

        List<OrderResponse> userTwoOrders = orderService.getUserOrders("usertwo");
        assertEquals(1, userTwoOrders.size());
        assertEquals(3, userTwoOrders.get(0).getQuantity());
    }

    @Test
    void getUserOrderById_OwnershipIsolation_ThrowsNotFoundForOtherUser() {
        OrderResponse orderOne = orderService.createBuyOrder("userone", new OrderRequest(UUID.randomUUID().toString(), "RELIANCE", 10));

        // Owner can fetch it
        OrderResponse fetched = orderService.getUserOrderById("userone", orderOne.getId());
        assertEquals(orderOne.getId(), fetched.getId());

        // Other user fetching it gets 404 (does not leak that order exists)
        assertThrows(ResourceNotFoundException.class, () ->
                orderService.getUserOrderById("usertwo", orderOne.getId()));
    }

    @Test
    void orderEntity_LifecycleTransitions_CannotTransitionFromTerminal() {
        Order order = new Order(
                UUID.randomUUID().toString(),
                testUser1,
                testStock,
                OrderSide.BUY,
                10,
                new BigDecimal("2500.00"),
                new BigDecimal("25000.00"),
                Instant.now()
        );
        assertEquals(OrderStatus.PENDING, order.getStatus());

        // Transition to CANCELLED
        order.transitionTo(OrderStatus.CANCELLED, null, null);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        // Attempting to transition from terminal state must throw IllegalStateException
        assertThrows(IllegalStateException.class, () ->
                order.transitionTo(OrderStatus.EXECUTED, new BigDecimal("2500.00"), Instant.now()));
    }
}
