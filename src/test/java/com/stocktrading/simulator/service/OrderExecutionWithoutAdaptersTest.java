package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.exception.SettlementUnavailableException;
import com.stocktrading.simulator.model.Order;
import com.stocktrading.simulator.model.OrderStatus;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderExecutionWithoutAdaptersTest {

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
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderExecutionService orderExecutionService;

    private User testUser;

    @BeforeEach
    void setup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(new User("Test User", "testuser", "user@example.com", "hash"));
        stockService.createStockIfAbsent("INFY", "Infosys Ltd", new BigDecimal("1500.00"));
    }

    @Test
    void applicationContext_StartsWithoutAdapters_AndExecutionFailsClosed() {
        OrderResponse createdOrder = orderService.createBuyOrder(
                "testuser",
                new OrderRequest(UUID.randomUUID().toString(), "INFY", 10)
        );

        // When execution is attempted against production bean without adapter beans registered
        assertThrows(SettlementUnavailableException.class, () ->
                orderExecutionService.executeOrder(createdOrder.getId(), "testuser")
        );

        // Verify order remains strictly PENDING
        Order orderAfter = orderRepository.findById(createdOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING, orderAfter.getStatus());
        assertNull(orderAfter.getExecutionPrice());
        assertNull(orderAfter.getExecutedAt());

        // Verify no Trade record is created
        assertEquals(0, tradeRepository.count());
    }
}
