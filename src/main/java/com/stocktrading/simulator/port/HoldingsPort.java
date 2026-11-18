package com.stocktrading.simulator.port;

import java.math.BigDecimal;

/**
 * Port defining portfolio holdings settlement operations required for order execution.
 *
 * <p>Concurrency & Locking Requirements for Implementing Adapters:
 * <ul>
 *   <li>All operations must participate in the caller's active database transaction (Propagation.MANDATORY / REQUIRED).</li>
 *   <li>Implementing adapters MUST NOT rely on separate quantity checks prior to deduction. Pre-checking shares
 *       and then deducting in separate statements creates a Time-of-Check to Time-of-Use (TOCTOU) race condition.</li>
 *   <li>Adapters must atomically verify and deduct available shares using either pessimistic row locks
 *       (e.g., {@code SELECT ... FOR UPDATE}) or atomic conditional SQL updates.</li>
 *   <li>If available shares are insufficient, the adapter must throw {@link com.stocktrading.simulator.exception.InsufficientSharesException},
 *       causing the enclosing settlement transaction to roll back cleanly.</li>
 * </ul>
 */
public interface HoldingsPort {

    /**
     * Atomically validates that the user possesses sufficient available shares and deducts them,
     * recording realized profit/loss using the exact execution price.
     *
     * @param userId the ID of the user
     * @param symbol the stock ticker symbol
     * @param quantity the number of shares to deduct (strictly positive)
     * @param executionPrice the exact per-share execution price
     * @param orderId the ID of the executing order for audit reference
     * @throws com.stocktrading.simulator.exception.InsufficientSharesException if user holds fewer unreserved shares than requested
     */
    void deductAvailableShares(Long userId, String symbol, int quantity, BigDecimal executionPrice, Long orderId);

    /**
     * Adds purchased shares to the user's portfolio upon successful BUY settlement,
     * updating the weighted-average cost basis using the exact execution price.
     *
     * @param userId the ID of the user
     * @param symbol the stock ticker symbol
     * @param quantity the number of shares to add (strictly positive)
     * @param executionPrice the exact per-share execution price
     * @param orderId the ID of the executing order for audit reference
     */
    void addShares(Long userId, String symbol, int quantity, BigDecimal executionPrice, Long orderId);
}
