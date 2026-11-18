package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.exception.InsufficientHoldingException;
import com.stocktrading.simulator.exception.InsufficientSharesException;
import com.stocktrading.simulator.model.Holding;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.port.HoldingsPort;
import com.stocktrading.simulator.repository.HoldingRepository;
import com.stocktrading.simulator.repository.StockRepository;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * JPA implementation of {@link HoldingsPort} managing persistent stock position holdings,
 * weighted average cost basis, and realized profit/loss calculations.
 *
 * <p>Concurrency & Transaction Guarantees:
 * <ul>
 *   <li>Enforces {@link Propagation#MANDATORY} to guarantee participation in the caller's active database transaction.</li>
 *   <li>Shares the exact same transaction boundary as {@link JpaWalletAdapter} and {@code OrderExecutionServiceImpl}.</li>
 *   <li>Uses pessimistic locking via {@code findByUserIdAndStockIdForUpdate} and entity-level optimistic locking
 *       via {@code @Version} on {@link Holding} to prevent overselling and concurrent update anomalies.</li>
 *   <li>Database constraint {@code uq_holding_user_stock} guarantees exactly one holding record per user and stock.</li>
 * </ul>
 */
@Component
public class JpaHoldingsAdapter implements HoldingsPort {

    public static final BigDecimal MIN_PRICE = Holding.MIN_STOCK_PRICE;
    public static final BigDecimal MAX_PRICE = Holding.MAX_STOCK_PRICE;

    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    public JpaHoldingsAdapter(HoldingRepository holdingRepository,
                              StockRepository stockRepository,
                              UserRepository userRepository) {
        this.holdingRepository = holdingRepository;
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void addShares(Long userId, String symbol, int quantity, BigDecimal executionPrice, Long orderId) {
        validateParameters(userId, symbol, quantity, executionPrice, orderId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found for userId: " + userId));

        Stock stock = stockRepository.findBySymbolIgnoreCase(symbol)
            .orElseThrow(() -> new IllegalStateException("Stock not found for symbol: " + symbol));

        Holding holding = holdingRepository.findByUserIdAndStockIdForUpdate(userId, stock.getId())
            .orElseGet(() -> new Holding(user, stock));

        holding.recordBuy(quantity, executionPrice);
        holdingRepository.saveAndFlush(holding);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductAvailableShares(Long userId, String symbol, int quantity, BigDecimal executionPrice, Long orderId) {
        validateParameters(userId, symbol, quantity, executionPrice, orderId);

        Stock stock = stockRepository.findBySymbolIgnoreCase(symbol)
            .orElseThrow(() -> new IllegalStateException("Stock not found for symbol: " + symbol));

        Holding holding = holdingRepository.findByUserIdAndStockIdForUpdate(userId, stock.getId())
            .orElseThrow(() -> new InsufficientHoldingException(
                "No shares held for stock: " + symbol + " by user: " + userId
            ));

        if (holding.getQuantity() < quantity) {
            throw new InsufficientHoldingException(
                "Insufficient shares owned: requested " + quantity + ", available " + holding.getQuantity()
            );
        }

        holding.recordSell(quantity, executionPrice);
        holdingRepository.saveAndFlush(holding);
    }

    private void validateParameters(Long userId, String symbol, int quantity, BigDecimal executionPrice, Long orderId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Stock symbol cannot be empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Share quantity must be strictly positive: " + quantity);
        }
        if (executionPrice == null || executionPrice.compareTo(MIN_PRICE) < 0) {
            throw new IllegalArgumentException("Execution price must be at least " + MIN_PRICE);
        }
        if (executionPrice.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Execution price exceeds maximum permitted limit: " + MAX_PRICE);
        }
        if (executionPrice.scale() > 2) {
            throw new IllegalArgumentException("Execution price cannot exceed 2 decimal places: " + executionPrice);
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
    }
}
