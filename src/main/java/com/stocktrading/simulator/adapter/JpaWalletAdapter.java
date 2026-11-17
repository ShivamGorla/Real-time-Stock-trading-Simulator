package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.exception.InsufficientFundsException;
import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.TransactionType;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.model.WalletTransaction;
import com.stocktrading.simulator.port.WalletPort;
import com.stocktrading.simulator.repository.WalletRepository;
import com.stocktrading.simulator.repository.WalletTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * JPA implementation of {@link WalletPort} managing persistent virtual wallet balance
 * deductions, credits, and immutable audit ledger entries.
 *
 * <p>Concurrency & Transaction Guarantees:
 * <ul>
 *   <li>Enforces {@link Propagation#MANDATORY} to guarantee participation in the caller's active database transaction.</li>
 *   <li>Never commits wallet modifications independently from order settlement.</li>
 *   <li>Leverages {@link Wallet} optimistic locking (via {@code @Version}) and unique constraint
 *       {@code uq_wallet_txn_wallet_ref} on {@code (wallet_id, reference_id)} to prevent double-spending.</li>
 *   <li>Translates wallet balance shortages into {@link InsufficientFundsException} for clean settlement rollback.</li>
 * </ul>
 */
@Component
public class JpaWalletAdapter implements WalletPort {

    /** Maximum value for MySQL / H2 DECIMAL(15,2) column: 9,999,999,999,999.99 */
    public static final BigDecimal MAX_WALLET_DECIMAL = new BigDecimal("9999999999999.99");
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    public static final String REQUIRED_CURRENCY = "INR";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public JpaWalletAdapter(WalletRepository walletRepository,
                            WalletTransactionRepository walletTransactionRepository) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void debitSufficientFunds(Long userId, BigDecimal amount, Long orderId, String reference) {
        validateParameters(userId, amount, orderId);

        Wallet wallet = getWalletOrThrow(userId);
        validateCurrency(wallet);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Insufficient funds in wallet: available " + wallet.getBalance() + " " + wallet.getCurrency() +
                ", required " + amount + " " + wallet.getCurrency()
            );
        }

        wallet.debit(amount);
        walletRepository.save(wallet);

        String referenceId = "ORDER-BUY-" + orderId;
        String description = (reference != null && !reference.isBlank()) ? reference : ("Stock Buy Order #" + orderId);

        WalletTransaction txn = new WalletTransaction(
                wallet,
                TransactionType.DEBIT,
                TransactionCategory.TRADE_BUY,
                amount,
                wallet.getBalance(),
                wallet.getCurrency(),
                referenceId,
                description
        );
        walletTransactionRepository.save(txn);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void creditFunds(Long userId, BigDecimal amount, Long orderId, String reference) {
        validateParameters(userId, amount, orderId);

        Wallet wallet = getWalletOrThrow(userId);
        validateCurrency(wallet);

        BigDecimal resultingBalance = wallet.getBalance().add(amount);
        if (resultingBalance.compareTo(MAX_WALLET_DECIMAL) > 0) {
            throw new IllegalStateException(
                "Credit of " + amount + " would cause wallet balance to exceed maximum allowed limit of " + MAX_WALLET_DECIMAL
            );
        }

        wallet.credit(amount);
        walletRepository.save(wallet);

        String referenceId = "ORDER-SELL-" + orderId;
        String description = (reference != null && !reference.isBlank()) ? reference : ("Stock Sell Order #" + orderId);

        WalletTransaction txn = new WalletTransaction(
                wallet,
                TransactionType.CREDIT,
                TransactionCategory.TRADE_SELL,
                amount,
                wallet.getBalance(),
                wallet.getCurrency(),
                referenceId,
                description
        );
        walletTransactionRepository.save(txn);
    }

    private Wallet getWalletOrThrow(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not provisioned for user ID: " + userId));
    }

    private void validateParameters(Long userId, BigDecimal amount, Long orderId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be strictly positive");
        }
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be strictly positive");
        }
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("Settlement amount must be at least " + MIN_AMOUNT);
        }
        if (amount.compareTo(MAX_WALLET_DECIMAL) > 0) {
            throw new IllegalStateException("Settlement amount " + amount + " exceeds maximum allowed limit of " + MAX_WALLET_DECIMAL);
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Settlement amount precision exceeds 2 decimal places: " + amount);
        }
    }

    private void validateCurrency(Wallet wallet) {
        if (!REQUIRED_CURRENCY.equalsIgnoreCase(wallet.getCurrency())) {
            throw new IllegalStateException("Unsupported wallet currency: " + wallet.getCurrency());
        }
    }
}
