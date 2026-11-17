package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.WalletResponse;
import com.stocktrading.simulator.dto.WalletTransactionResponse;
import com.stocktrading.simulator.exception.IdempotencyConflictException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.repository.WalletRepository;
import com.stocktrading.simulator.repository.WalletTransactionRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Service managing persistent virtual wallets and the immutable transaction ledger.
 *
 * <p><strong>Architecture Contract:</strong></p>
 * <ul>
 *   <li><strong>Registration:</strong> {@link #createWalletWithInitialBalance(User)} participates in
 *       the registration transaction via {@code Propagation.REQUIRED}. User, Wallet, and the initial
 *       ledger entry are committed atomically.</li>
 *   <li><strong>Standalone Operations:</strong> {@link #executeDebit} and {@link #executeCredit} run standalone
 *       outside any existing transaction, using an internal {@link TransactionTemplate}. They reject invocation
 *       inside an active transaction to guarantee that commit-time exception handling, rollback, and
 *       subsequent idempotency recovery operate on an isolated transaction boundary.</li>
 *   <li><strong>Future Trading Integration:</strong> Future trading modules (Phase 3) must use
 *       transaction-participating methods annotated with {@code @Transactional(propagation = Propagation.MANDATORY)}
 *       invoked through Spring bean proxies. This ensures that order persistence, wallet balance debit, and
 *       ledger records commit or roll back in a single atomic database transaction.</li>
 * </ul>
 */
@Service
public class WalletService {

    public static final BigDecimal INITIAL_BALANCE = new BigDecimal("100000.00");
    public static final String DEFAULT_CURRENCY = "INR";
    public static final int MAX_PAGE_SIZE = 100;

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TransactionTemplate transactionTemplate;

    public WalletService(WalletRepository walletRepository,
                         UserRepository userRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         PlatformTransactionManager transactionManager) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Atomically creates a persistent virtual wallet and its initial balance ledger entry.
     * Joins the caller's transaction via {@code Propagation.REQUIRED}.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Wallet createWalletWithInitialBalance(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Persisted user is required to create a wallet");
        }
        if (walletRepository.existsByUserId(user.getId())) {
            throw new IllegalStateException("Wallet already exists for user ID: " + user.getId());
        }

        Wallet wallet = new Wallet(user, INITIAL_BALANCE, DEFAULT_CURRENCY);
        wallet = walletRepository.save(wallet);

        String initialRefId = "INIT-" + user.getId();
        WalletTransaction initialTxn = new WalletTransaction(
            wallet,
            TransactionType.CREDIT,
            TransactionCategory.INITIAL_BALANCE,
            INITIAL_BALANCE,
            wallet.getBalance(),
            wallet.getCurrency(),
            initialRefId,
            "Initial virtual balance on user registration"
        );
        walletTransactionRepository.save(initialTxn);

        return wallet;
    }

    /**
     * Executes a standalone debit operation with wallet-scoped idempotency.
     * Must NOT be called within an active transaction.
     */
    public WalletTransaction executeDebit(Long userId,
                                          BigDecimal amount,
                                          TransactionCategory category,
                                          String referenceId,
                                          String description) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "executeDebit() must not be called within an active transaction. " +
                "Future trading operations must use transaction-participating methods."
            );
        }

        validateMutationParameters(userId, amount, category, referenceId);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("Wallet not found for user ID: " + userId));

        // Step 1: Idempotency pre-check
        Optional<WalletTransaction> existing =
            walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), referenceId);
        if (existing.isPresent()) {
            validateIdempotentPayload(existing.get(), TransactionType.DEBIT, category, amount);
            return existing.get();
        }

        // Step 2: Execute mutation inside TransactionTemplate
        try {
            return transactionTemplate.execute(status -> {
                Wallet currentWallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("Wallet not found for user ID: " + userId));

                currentWallet.debit(amount);
                walletRepository.save(currentWallet);

                WalletTransaction txn = new WalletTransaction(
                    currentWallet,
                    TransactionType.DEBIT,
                    category,
                    amount,
                    currentWallet.getBalance(),
                    currentWallet.getCurrency(),
                    referenceId,
                    description
                );
                return walletTransactionRepository.save(txn);
            });
        } catch (DataIntegrityViolationException ex) {
            // Step 3: Catch commit-time unique constraint violation (concurrent identical request)
            // The failed transaction has rolled back. Query fresh to recover winning record.
            return walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), referenceId)
                .map(winner -> {
                    validateIdempotentPayload(winner, TransactionType.DEBIT, category, amount);
                    return winner;
                })
                .orElseThrow(() -> ex);
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException ex) {
            throw ex;
        }
    }

    /**
     * Executes a standalone credit operation with wallet-scoped idempotency.
     * Must NOT be called within an active transaction.
     */
    public WalletTransaction executeCredit(Long userId,
                                           BigDecimal amount,
                                           TransactionCategory category,
                                           String referenceId,
                                           String description) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "executeCredit() must not be called within an active transaction. " +
                "Future trading operations must use transaction-participating methods."
            );
        }

        validateMutationParameters(userId, amount, category, referenceId);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("Wallet not found for user ID: " + userId));

        // Step 1: Idempotency pre-check
        Optional<WalletTransaction> existing =
            walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), referenceId);
        if (existing.isPresent()) {
            validateIdempotentPayload(existing.get(), TransactionType.CREDIT, category, amount);
            return existing.get();
        }

        // Step 2: Execute mutation inside TransactionTemplate
        try {
            return transactionTemplate.execute(status -> {
                Wallet currentWallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("Wallet not found for user ID: " + userId));

                currentWallet.credit(amount);
                walletRepository.save(currentWallet);

                WalletTransaction txn = new WalletTransaction(
                    currentWallet,
                    TransactionType.CREDIT,
                    category,
                    amount,
                    currentWallet.getBalance(),
                    currentWallet.getCurrency(),
                    referenceId,
                    description
                );
                return walletTransactionRepository.save(txn);
            });
        } catch (DataIntegrityViolationException ex) {
            // Step 3: Catch commit-time unique constraint violation (concurrent identical request)
            return walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), referenceId)
                .map(winner -> {
                    validateIdempotentPayload(winner, TransactionType.CREDIT, category, amount);
                    return winner;
                })
                .orElseThrow(() -> ex);
        } catch (ObjectOptimisticLockingFailureException | PessimisticLockingFailureException ex) {
            throw ex;
        }
    }

    /**
     * Returns paginated transaction history for the authenticated user's wallet.
     */
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactionHistory(String username,
                                                                 TransactionType type,
                                                                 TransactionCategory category,
                                                                 Instant from,
                                                                 Instant to,
                                                                 Pageable pageable) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        Wallet wallet = walletRepository.findByUserId(user.getId())
            .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + username));

        int pageSize = (pageable != null) ? Math.min(pageable.getPageSize(), MAX_PAGE_SIZE) : 20;
        int pageNumber = (pageable != null) ? pageable.getPageNumber() : 0;
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable effectivePageable = PageRequest.of(pageNumber, pageSize, sort);

        return walletTransactionRepository
            .findFilteredTransactions(wallet.getId(), type, category, from, to, effectivePageable)
            .map(WalletTransactionResponse::new);
    }

    @Transactional(readOnly = true)
    public Wallet getWalletByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + username));
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletResponseByUsername(String username) {
        Wallet wallet = getWalletByUsername(username);
        return new WalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public Optional<Wallet> findWalletByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return walletRepository.findByUserId(userId);
    }

    private void validateMutationParameters(Long userId,
                                            BigDecimal amount,
                                            TransactionCategory category,
                                            String referenceId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly positive");
        }
        if (category == null) {
            throw new IllegalArgumentException("Transaction category cannot be null");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be empty");
        }
    }

    private void validateIdempotentPayload(WalletTransaction existing,
                                           TransactionType expectedType,
                                           TransactionCategory expectedCategory,
                                           BigDecimal expectedAmount) {
        if (existing.getType() != expectedType ||
            existing.getCategory() != expectedCategory ||
            existing.getAmount().compareTo(expectedAmount) != 0) {
            throw new IdempotencyConflictException(
                "Reference ID '" + existing.getReferenceId() + "' already exists with conflicting payload"
            );
        }
    }
}
