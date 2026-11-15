package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.exception.SettlementUnavailableException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.port.HoldingsPort;
import com.stocktrading.simulator.port.WalletPort;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.StockRepository;
import com.stocktrading.simulator.repository.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Service
public class OrderExecutionServiceImpl implements OrderExecutionService {

    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_EXECUTION_PRICE = new BigDecimal("9999999999.99");
    private static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("99999999999999.99");

    private final OrderRepository orderRepository;
    private final StockRepository stockRepository;
    private final TradeRepository tradeRepository;
    private final Optional<WalletPort> walletPort;
    private final Optional<HoldingsPort> holdingsPort;

    public OrderExecutionServiceImpl(OrderRepository orderRepository,
                                     StockRepository stockRepository,
                                     TradeRepository tradeRepository,
                                     Optional<WalletPort> walletPort,
                                     Optional<HoldingsPort> holdingsPort) {
        this.orderRepository = orderRepository;
        this.stockRepository = stockRepository;
        this.tradeRepository = tradeRepository;
        this.walletPort = walletPort;
        this.holdingsPort = holdingsPort;
    }

    @Override
    public boolean isSettlementAvailable() {
        return walletPort.isPresent() && holdingsPort.isPresent();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OrderResponse executeOrder(Long orderId, String username) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be positive");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));

        if (!order.getUser().getUsername().equals(username.trim())) {
            // Prevent cross-user information leakage
            throw new ResourceNotFoundException("Order with id " + orderId + " not found");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Order with id " + orderId + " is not in PENDING state (current: " + order.getStatus() + ")");
        }

        // Fail closed if either required settlement adapter is missing
        if (walletPort.isEmpty() || holdingsPort.isEmpty()) {
            throw new SettlementUnavailableException("Settlement service is unavailable: required wallet or holdings adapter is not configured");
        }

        // Resolve current stock price server-side at settlement time
        Stock stock = stockRepository.findById(order.getStock().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found for order id: " + orderId));

        BigDecimal rawPrice = stock.getCurrentPrice();
        if (rawPrice == null || rawPrice.compareTo(MIN_PRICE) < 0) {
            throw new IllegalStateException("Execution price for stock " + stock.getSymbol() + " must be at least " + MIN_PRICE);
        }

        BigDecimal executionPrice = rawPrice.setScale(2, RoundingMode.HALF_UP);
        if (executionPrice.compareTo(MAX_EXECUTION_PRICE) > 0) {
            throw new IllegalStateException("Execution price " + executionPrice + " exceeds maximum allowed limit of " + MAX_EXECUTION_PRICE);
        }

        Integer quantity = order.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new IllegalStateException("Order quantity must be strictly positive");
        }

        BigDecimal totalExecutionAmount = executionPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        if (totalExecutionAmount.compareTo(MIN_PRICE) < 0) {
            throw new IllegalStateException("Total execution amount must be at least " + MIN_PRICE);
        }
        if (totalExecutionAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {
            throw new IllegalStateException("Total execution amount " + totalExecutionAmount + " exceeds maximum allowed monetary limit of " + MAX_TOTAL_AMOUNT);
        }

        Instant executionTime = Instant.now();
        Long userId = order.getUser().getId();
        String symbol = stock.getSymbol();

        // Perform settlement mutations atomically
        WalletPort wallet = walletPort.get();
        HoldingsPort holdings = holdingsPort.get();

        if (order.getSide() == OrderSide.BUY) {
            wallet.debitSufficientFunds(userId, totalExecutionAmount, orderId, "BUY settlement for " + symbol);
            holdings.addShares(userId, symbol, quantity, orderId);
        } else if (order.getSide() == OrderSide.SELL) {
            holdings.deductAvailableShares(userId, symbol, quantity, orderId);
            wallet.creditFunds(userId, totalExecutionAmount, orderId, "SELL settlement for " + symbol);
        } else {
            throw new IllegalArgumentException("Unsupported order side: " + order.getSide());
        }

        // Record the executed trade audit entity
        Trade trade = new Trade(
                order,
                order.getUser(),
                stock,
                order.getSide(),
                quantity,
                executionPrice,
                totalExecutionAmount,
                executionTime
        );
        tradeRepository.save(trade);

        // Transition order status to EXECUTED only after all financial changes succeed
        order.transitionTo(OrderStatus.EXECUTED, executionPrice, executionTime);
        Order savedOrder = orderRepository.save(order);

        return new OrderResponse(savedOrder);
    }
}
