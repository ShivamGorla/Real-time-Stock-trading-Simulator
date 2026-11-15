package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.exception.DuplicateOrderException;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.StockRepository;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("99999999999999.99");

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    @Value("${stock.order.max-quantity:100000}")
    private int maxQuantity;

    public OrderService(OrderRepository orderRepository,
                        UserRepository userRepository,
                        StockRepository stockRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public OrderResponse createBuyOrder(String username, OrderRequest request) {
        return createOrder(username, request, OrderSide.BUY);
    }

    @Transactional
    public OrderResponse createSellOrder(String username, OrderRequest request) {
        return createOrder(username, request, OrderSide.SELL);
    }

    @Transactional
    public OrderResponse createOrder(String username, OrderRequest request, OrderSide side) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("Order request cannot be null");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        String clientOrderId = request.getClientOrderId() != null ? request.getClientOrderId().trim() : "";
        if (clientOrderId.isEmpty()) {
            throw new IllegalArgumentException("Client order ID is required");
        }

        // Idempotency check: Reject duplicate submission for this user
        if (orderRepository.existsByUserAndClientOrderId(user, clientOrderId)) {
            throw new DuplicateOrderException("An order with clientOrderId '" + clientOrderId + "' already exists for this user");
        }

        Integer quantity = request.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (quantity > maxQuantity) {
            throw new IllegalArgumentException("Quantity exceeds maximum allowed limit of " + maxQuantity + " shares");
        }

        String symbol = request.getSymbol() != null ? request.getSymbol().trim() : "";
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Stock symbol is required");
        }

        Stock stock = stockRepository.findBySymbolIgnoreCase(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock with symbol '" + symbol.toUpperCase() + "' not found"));

        // Capture server-determined quoted price snapshot (never trust client)
        BigDecimal quotedPrice = stock.getCurrentPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalQuotedAmount = quotedPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        if (totalQuotedAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {
            throw new IllegalArgumentException("Total order amount exceeds allowed monetary system limits");
        }

        Order order = new Order(
                clientOrderId,
                user,
                stock,
                side,
                quantity,
                quotedPrice,
                totalQuotedAmount,
                Instant.now()
        );

        try {
            Order saved = orderRepository.save(order);
            return new OrderResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent submission with the same clientOrderId caught by database unique constraint
            throw new DuplicateOrderException("An order with clientOrderId '" + clientOrderId + "' was concurrently submitted");
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(String username) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return orderRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(OrderResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderResponse getUserOrderById(String username, Long orderId) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Order order = orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        return new OrderResponse(order);
    }

    public int getMaxQuantity() {
        return maxQuantity;
    }
}
