package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.TransactionType;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.model.WalletTransaction;
import com.stocktrading.simulator.port.WalletTopUpPort;
import com.stocktrading.simulator.repository.WalletRepository;
import com.stocktrading.simulator.repository.WalletTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * JPA implementation of {@link WalletTopUpPort} backed by the persistent wallet and immutable ledger.
 *
 * <p>Concurrency & Transaction Guarantees:
 * <ul>
 *   <li>Enforces {@link Propagation#MANDATORY} to participate strictly inside {@code PaymentService}'s
 *       active database transaction — never invokes standalone wallet methods.</li>
 *   <li>Credits the persistent {@link Wallet} and records exactly one {@link TransactionCategory#TOP_UP}
 *       {@link WalletTransaction} keyed by {@code paymentReference}.</li>
 *   <li>Enforces {@link JpaWalletAdapter#MAX_WALLET_DECIMAL} overflow protection, currency integrity,
 *       optimistic locking ({@code @Version}), and wallet-scoped unique reference constraint
 *       ({@code uq_wallet_txn_wallet_ref}).</li>
 *   <li>Any failure rolls back the enclosing payment {@code PENDING -> SUCCESS} transition atomically.</li>
 * </ul>
 */
@Component
public class JpaWalletTopUpAdapter implements WalletTopUpPort {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public JpaWalletTopUpAdapter(WalletRepository walletRepository,
                                 WalletTransactionRepository walletTransactionRepository) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void creditTopUp(Long userId, BigDecimal amount, String paymentReference) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(JpaWalletAdapter.MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("Top-up amount must be at least " + JpaWalletAdapter.MIN_AMOUNT);
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Top-up amount must have at most 2 decimal places");
        }
        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required for wallet top-up");
        }

        BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        String ref = paymentReference.trim();

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("Wallet not found for user ID: " + userId));

        if (!JpaWalletAdapter.REQUIRED_CURRENCY.equals(wallet.getCurrency())) {
            throw new IllegalStateException(
                "Wallet currency mismatch: expected " + JpaWalletAdapter.REQUIRED_CURRENCY
                    + ", found " + wallet.getCurrency()
            );
        }

        BigDecimal resultingBalance = wallet.getBalance().add(normalizedAmount);
        if (resultingBalance.compareTo(JpaWalletAdapter.MAX_WALLET_DECIMAL) > 0) {
            throw new IllegalStateException(
                "Top-up of " + normalizedAmount + " would cause wallet balance to exceed maximum allowed limit of "
                    + JpaWalletAdapter.MAX_WALLET_DECIMAL
            );
        }

        wallet.credit(normalizedAmount);
        walletRepository.saveAndFlush(wallet);

        WalletTransaction txn = new WalletTransaction(
            wallet,
            TransactionType.CREDIT,
            TransactionCategory.TOP_UP,
            normalizedAmount,
            wallet.getBalance(),
            wallet.getCurrency(),
            ref,
            "Simulated virtual wallet top-up (" + ref + ")"
        );
        walletTransactionRepository.saveAndFlush(txn);
    }
}
