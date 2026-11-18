package com.stocktrading.simulator.port;

import java.math.BigDecimal;

/**
 * Contract for crediting virtual-money top-ups into the persistent wallet and ledger.
 *
 * <p>Requirements for implementing adapters:
 * <ul>
 *   <li>Must participate in the caller's active database transaction ({@code Propagation.MANDATORY})
 *       so wallet credit, ledger insertion, and payment status transition commit or roll back atomically.</li>
 *   <li>Must enforce wallet balance upper limits and optimistic locking.</li>
 *   <li>Must persist exactly one {@code TOP_UP} wallet ledger entry keyed by {@code paymentReference}.</li>
 * </ul>
 */
public interface WalletTopUpPort {

    /**
     * @param userId           internal user id
     * @param amount           strictly positive, scale 2
     * @param paymentReference e.g. "TOPUP-42", used as wallet-scoped idempotency reference
     */
    void creditTopUp(Long userId, BigDecimal amount, String paymentReference);
}
