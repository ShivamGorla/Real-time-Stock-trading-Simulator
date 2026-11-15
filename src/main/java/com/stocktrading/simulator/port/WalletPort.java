package com.stocktrading.simulator.port;

import java.math.BigDecimal;

/**
 * Port defining wallet financial settlement operations required for order execution.
 *
 * <p>Concurrency & Locking Requirements for Implementing Adapters:
 * <ul>
 *   <li>All operations must participate in the caller's active database transaction (Propagation.REQUIRED).</li>
 *   <li>Implementing adapters MUST NOT rely on separate balance checks prior to debiting. Pre-checking balance
 *       and then debiting in separate statements creates a Time-of-Check to Time-of-Use (TOCTOU) race condition.</li>
 *   <li>Adapters must atomically verify and deduct available funds using either pessimistic row locks
 *       (e.g., {@code SELECT ... FOR UPDATE}) or atomic conditional SQL updates
 *       (e.g., {@code UPDATE wallets SET balance = balance - :amount WHERE user_id = :userId AND balance >= :amount}).</li>
 *   <li>If unreserved funds are insufficient, the adapter must throw {@link com.stocktrading.simulator.exception.InsufficientFundsException},
 *       causing the enclosing settlement transaction to roll back cleanly.</li>
 * </ul>
 */
public interface WalletPort {

    /**
     * Atomically validates that the user possesses sufficient available funds and deducts the amount.
     *
     * @param userId the ID of the user
     * @param amount the exact settlement amount to debit (strictly positive)
     * @param orderId the ID of the executing order for audit reference
     * @param reference an audit reference description
     * @throws com.stocktrading.simulator.exception.InsufficientFundsException if unreserved funds are insufficient
     */
    void debitSufficientFunds(Long userId, BigDecimal amount, Long orderId, String reference);

    /**
     * Credits settled proceeds to the user's wallet.
     *
     * @param userId the ID of the user
     * @param amount the exact settlement amount to credit (strictly positive)
     * @param orderId the ID of the executing order for audit reference
     * @param reference an audit reference description
     */
    void creditFunds(Long userId, BigDecimal amount, Long orderId, String reference);
}
