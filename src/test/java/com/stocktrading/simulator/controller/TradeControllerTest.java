package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    private User user1;
    private User user2;
    private Stock stock;

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

        user1 = userRepository.save(new User("Test User 1", "testuser1", "user1@example.com", "hash1"));
        user2 = userRepository.save(new User("Test User 2", "testuser2", "user2@example.com", "hash2"));
        stock = stockService.createStockIfAbsent("INFY", "Infosys Ltd", new BigDecimal("1500.00"));
    }

    @Test
    void unauthenticated_TradeEndpoints_Return401() throws Exception {
        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/trades/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getUserTrades_EmptyHistory_ReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getUserTrades_WithTrades_ReturnsPaginatedList() throws Exception {
        Order order1 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now().minusSeconds(60)
        ));
        order1.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now().minusSeconds(60));
        orderRepository.save(order1);

        Trade trade1 = tradeRepository.save(new Trade(
                order1, user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now().minusSeconds(60)
        ));

        Order order2 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.SELL, 5,
                new BigDecimal("1520.00"), new BigDecimal("7600.00"), Instant.now()
        ));
        order2.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1520.00"), Instant.now());
        orderRepository.save(order2);

        Trade trade2 = tradeRepository.save(new Trade(
                order2, user1, stock, OrderSide.SELL, 5,
                new BigDecimal("1520.00"), new BigDecimal("7600.00"), Instant.now()
        ));

        mockMvc.perform(get("/api/trades?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].symbol").value("INFY"))
                .andExpect(jsonPath("$.content[0].side").value("SELL"))
                .andExpect(jsonPath("$.content[0].quantity").value(5))
                .andExpect(jsonPath("$.content[0].executionPrice").value(1520.00))
                .andExpect(jsonPath("$.content[0].totalAmount").value(7600.00))
                .andExpect(jsonPath("$.content[1].side").value("BUY"))
                .andExpect(jsonPath("$.content[1].quantity").value(10));
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getTradeById_OwnedByUser_ReturnsTrade() throws Exception {
        Order order = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 15,
                new BigDecimal("1500.00"), new BigDecimal("22500.00"), Instant.now()
        ));
        order.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
        orderRepository.save(order);

        Trade trade = tradeRepository.save(new Trade(
                order, user1, stock, OrderSide.BUY, 15,
                new BigDecimal("1500.00"), new BigDecimal("22500.00"), Instant.now()
        ));

        mockMvc.perform(get("/api/trades/" + trade.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trade.getId()))
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.symbol").value("INFY"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(15))
                .andExpect(jsonPath("$.executionPrice").value(1500.00))
                .andExpect(jsonPath("$.totalAmount").value(22500.00));
    }

    @Test
    @WithMockUser(username = "testuser2")
    void getTradeById_NotOwnedByUser_Returns404() throws Exception {
        Order order = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 5,
                new BigDecimal("1500.00"), new BigDecimal("7500.00"), Instant.now()
        ));
        order.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
        orderRepository.save(order);

        Trade trade = tradeRepository.save(new Trade(
                order, user1, stock, OrderSide.BUY, 5,
                new BigDecimal("1500.00"), new BigDecimal("7500.00"), Instant.now()
        ));

        // User 2 requests User 1's trade -> returns 404
        mockMvc.perform(get("/api/trades/" + trade.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Trade with id " + trade.getId() + " not found"));
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getUserTrades_PageSizeCappedAt100() throws Exception {
        for (int i = 1; i <= 105; i++) {
            Order order = orderRepository.save(new Order(
                    UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, i,
                    new BigDecimal("1500.00"), BigDecimal.valueOf(1500L * i), Instant.now()
            ));
            order.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
            orderRepository.save(order);

            tradeRepository.save(new Trade(
                    order, user1, stock, OrderSide.BUY, i,
                    new BigDecimal("1500.00"), BigDecimal.valueOf(1500L * i), Instant.now()
            ));
        }

        mockMvc.perform(get("/api/trades?page=0&size=150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(100)))
                .andExpect(jsonPath("$.totalElements").value(105));
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getUserTrades_NegativePageAndInvalidSize_HandledSafely() throws Exception {
        Order order = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 5,
                new BigDecimal("1500.00"), new BigDecimal("7500.00"), Instant.now()
        ));
        order.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
        orderRepository.save(order);

        tradeRepository.save(new Trade(
                order, user1, stock, OrderSide.BUY, 5,
                new BigDecimal("1500.00"), new BigDecimal("7500.00"), Instant.now()
        ));

        mockMvc.perform(get("/api/trades?page=-1&size=-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getUserTrades_UnsafeSortField_FallsBackToExecutedAtDescSafely() throws Exception {
        Order order1 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now().minusSeconds(100)
        ));
        order1.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now().minusSeconds(100));
        orderRepository.save(order1);

        tradeRepository.save(new Trade(
                order1, user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now().minusSeconds(100)
        ));

        Order order2 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 20,
                new BigDecimal("1500.00"), new BigDecimal("30000.00"), Instant.now()
        ));
        order2.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
        orderRepository.save(order2);

        tradeRepository.save(new Trade(
                order2, user1, stock, OrderSide.BUY, 20,
                new BigDecimal("1500.00"), new BigDecimal("30000.00"), Instant.now()
        ));

        mockMvc.perform(get("/api/trades?sort=maliciousProperty,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                // Falls back to executedAt DESC, so order2 (more recent) comes first
                .andExpect(jsonPath("$.content[0].quantity").value(20))
                .andExpect(jsonPath("$.content[1].quantity").value(10));
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getUserTrades_ValidCustomSort_OrdersCorrectly() throws Exception {
        Order order1 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 50,
                new BigDecimal("1500.00"), new BigDecimal("75000.00"), Instant.now().minusSeconds(50)
        ));
        order1.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now().minusSeconds(50));
        orderRepository.save(order1);

        tradeRepository.save(new Trade(
                order1, user1, stock, OrderSide.BUY, 50,
                new BigDecimal("1500.00"), new BigDecimal("75000.00"), Instant.now().minusSeconds(50)
        ));

        Order order2 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now()
        ));
        order2.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
        orderRepository.save(order2);

        tradeRepository.save(new Trade(
                order2, user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now()
        ));

        mockMvc.perform(get("/api/trades?sort=quantity,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                // Sort by quantity ASC: order2 (quantity 10) first, then order1 (quantity 50)
                .andExpect(jsonPath("$.content[0].quantity").value(10))
                .andExpect(jsonPath("$.content[1].quantity").value(50));
    }

    @Test
    @WithMockUser(username = "testuser1")
    void getUserTrades_UserIsolationPreserved() throws Exception {
        Order order1 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now()
        ));
        order1.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
        orderRepository.save(order1);

        tradeRepository.save(new Trade(
                order1, user1, stock, OrderSide.BUY, 10,
                new BigDecimal("1500.00"), new BigDecimal("15000.00"), Instant.now()
        ));

        Order order2 = orderRepository.save(new Order(
                UUID.randomUUID().toString(), user2, stock, OrderSide.BUY, 20,
                new BigDecimal("1500.00"), new BigDecimal("30000.00"), Instant.now()
        ));
        order2.transitionTo(OrderStatus.EXECUTED, new BigDecimal("1500.00"), Instant.now());
        orderRepository.save(order2);

        tradeRepository.save(new Trade(
                order2, user2, stock, OrderSide.BUY, 20,
                new BigDecimal("1500.00"), new BigDecimal("30000.00"), Instant.now()
        ));

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].quantity").value(10));
    }
}
