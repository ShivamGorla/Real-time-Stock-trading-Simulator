package com.stocktrading.simulator.service;

import com.stocktrading.simulator.exception.IdempotencyConflictException;
import com.stocktrading.simulator.exception.InsufficientBalanceException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.TradeRepository;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.repository.WalletRepository;
import com.stocktrading.simulator.repository.WalletTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WalletTransactionServiceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createTestUser(String username) {
        User user = new User("Test " + username, username, username + "@example.com", "hash123");
        user = userRepository.save(user);
        walletService.createWalletWithInitialBalance(user);
        return user;
    }

    @Test
    void executeCredit_ValidRequest_IncreasesBalanceAndCreatesLedgerEntry() {
        User user = createTestUser("credituser");
        BigDecimal creditAmount = new BigDecimal("5000.00");

        WalletTransaction txn = walletService.executeCredit(
            user.getId(),
            creditAmount,
            TransactionCategory.TOP_UP,
            "TOPUP-101",
            "Account top-up"
        );

        assertNotNull(txn.getId());
        assertEquals(TransactionType.CREDIT, txn.getType());
        assertEquals(TransactionCategory.TOP_UP, txn.getCategory());
        assertEquals(0, new BigDecimal("5000.00").compareTo(txn.getAmount()));
        assertEquals(0, new BigDecimal("105000.00").compareTo(txn.getBalanceAfter()));
        assertEquals("TOPUP-101", txn.getReferenceId());

        Wallet updatedWallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("105000.00").compareTo(updatedWallet.getBalance()));
    }

    @Test
    void executeDebit_ValidRequest_DecreasesBalanceAndCreatesLedgerEntry() {
        User user = createTestUser("debituser");
        BigDecimal debitAmount = new BigDecimal("25000.00");

        WalletTransaction txn = walletService.executeDebit(
            user.getId(),
            debitAmount,
            TransactionCategory.TRADE_BUY,
            "ORDER-201",
            "Stock purchase 50 shares"
        );

        assertNotNull(txn.getId());
        assertEquals(TransactionType.DEBIT, txn.getType());
        assertEquals(TransactionCategory.TRADE_BUY, txn.getCategory());
        assertEquals(0, new BigDecimal("25000.00").compareTo(txn.getAmount()));
        assertEquals(0, new BigDecimal("75000.00").compareTo(txn.getBalanceAfter()));
        assertEquals("ORDER-201", txn.getReferenceId());

        Wallet updatedWallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("75000.00").compareTo(updatedWallet.getBalance()));
    }

    @Test
    void executeDebit_InsufficientBalance_ThrowsExceptionAndLeavesBalanceUnchanged() {
        User user = createTestUser("brokeuser");
        BigDecimal excessiveAmount = new BigDecimal("150000.00");

        long initialTxnCount = walletTransactionRepository.count();

        assertThrows(InsufficientBalanceException.class, () ->
            walletService.executeDebit(
                user.getId(),
                excessiveAmount,
                TransactionCategory.TRADE_BUY,
                "ORDER-BROKE-1",
                "Excessive order"
            )
        );

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet.getBalance()));
        assertEquals(initialTxnCount, walletTransactionRepository.count(),
            "No ledger entry should be created when debit fails due to insufficient balance");
    }

    @Test
    void executeDebit_IdempotentReplay_ReturnsExistingTransactionWithoutDoubleDebit() {
        User user = createTestUser("idempotentuser");
        BigDecimal amount = new BigDecimal("10000.00");
        String refId = "ORDER-IDEM-1";

        WalletTransaction firstTxn = walletService.executeDebit(
            user.getId(), amount, TransactionCategory.TRADE_BUY, refId, "First call"
        );

        // Replay with identical payload
        WalletTransaction secondTxn = walletService.executeDebit(
            user.getId(), amount, TransactionCategory.TRADE_BUY, refId, "Replay call"
        );

        assertEquals(firstTxn.getId(), secondTxn.getId());
        assertEquals(firstTxn.getBalanceAfter(), secondTxn.getBalanceAfter());

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("90000.00").compareTo(wallet.getBalance()),
            "Wallet must only be debited once for repeated idempotent reference ID");
    }

    @Test
    void executeCredit_IdempotentReplay_ReturnsExistingTransactionWithoutDoubleCredit() {
        User user = createTestUser("idempotentcreditor");
        BigDecimal amount = new BigDecimal("20000.00");
        String refId = "TOPUP-IDEM-1";

        WalletTransaction firstTxn = walletService.executeCredit(
            user.getId(), amount, TransactionCategory.TOP_UP, refId, "First call"
        );

        WalletTransaction secondTxn = walletService.executeCredit(
            user.getId(), amount, TransactionCategory.TOP_UP, refId, "Second call"
        );

        assertEquals(firstTxn.getId(), secondTxn.getId());
        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("120000.00").compareTo(wallet.getBalance()),
            "Wallet must only be credited once for repeated idempotent reference ID");
    }

    @Test
    void executeDebit_IdempotentConflict_DifferentAmount_ThrowsIdempotencyConflictException() {
        User user = createTestUser("conflictuser");
        String refId = "ORDER-CONFLICT-1";

        walletService.executeDebit(
            user.getId(), new BigDecimal("5000.00"), TransactionCategory.TRADE_BUY, refId, "Original"
        );

        assertThrows(IdempotencyConflictException.class, () ->
            walletService.executeDebit(
                user.getId(), new BigDecimal("9999.00"), TransactionCategory.TRADE_BUY, refId, "Tampered amount"
            )
        );
    }

    @Test
    void executeDebit_IdempotentConflict_DifferentCategory_ThrowsIdempotencyConflictException() {
        User user = createTestUser("conflictcatuser");
        String refId = "REF-CONFLICT-CAT";

        walletService.executeDebit(
            user.getId(), new BigDecimal("5000.00"), TransactionCategory.TRADE_BUY, refId, "Original"
        );

        assertThrows(IdempotencyConflictException.class, () ->
            walletService.executeDebit(
                user.getId(), new BigDecimal("5000.00"), TransactionCategory.ADJUSTMENT, refId, "Tampered category"
            )
        );
    }

    @Test
    void executeDebit_MissingWallet_ThrowsIllegalStateException_DoesNotCreateWallet() {
        assertThrows(IllegalStateException.class, () ->
            walletService.executeDebit(
                99999L, new BigDecimal("100.00"), TransactionCategory.TRADE_BUY, "REF-NOWALLET", "No wallet"
            )
        );
    }

    @Test
    void executeDebit_CalledInsideActiveTransaction_ThrowsIllegalStateException() {
        User user = createTestUser("txtrapuser");
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () ->
            template.execute(status ->
                walletService.executeDebit(
                    user.getId(),
                    new BigDecimal("1000.00"),
                    TransactionCategory.TRADE_BUY,
                    "ORDER-TX-TRAP",
                    "Should fail fast"
                )
            )
        );
    }

    @Test
    void concurrentDuplicateRequests_SameReferenceId_ExactlyOneLedgerEntryPersisted() throws Exception {
        User user = createTestUser("concurrentduplicateuser");
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        BigDecimal debitAmount = new BigDecimal("1000.00");
        String sharedRefId = "ORDER-CONCURRENT-DUP";

        List<WalletTransaction> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    WalletTransaction txn = walletService.executeDebit(
                        user.getId(),
                        debitAmount,
                        TransactionCategory.TRADE_BUY,
                        sharedRefId,
                        "Concurrent duplicate test"
                    );
                    results.add(txn);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads must complete within timeout");
        assertTrue(errors.isEmpty(), "No unhandled errors expected during concurrent duplicate requests: " + errors);
        assertEquals(threadCount, results.size());

        // Every thread should receive the same winning transaction ID
        Long expectedId = results.get(0).getId();
        for (WalletTransaction txn : results) {
            assertEquals(expectedId, txn.getId());
        }

        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("99000.00").compareTo(wallet.getBalance()),
            "Wallet balance should be debited exactly once");

        long count = walletTransactionRepository.findAll().stream()
            .filter(t -> sharedRefId.equals(t.getReferenceId()))
            .count();
        assertEquals(1, count, "Exactly one ledger entry must exist for referenceId " + sharedRefId);
    }

    @Test
    void concurrentDebits_CompeteForInsufficientBalance_CannotProduceNegativeBalance() throws Exception {
        User user = createTestUser("negativebalanceguard");
        // Balance is 100,000. We set balance to 100.00 to test competition:
        Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        wallet.setBalance(new BigDecimal("100.00"));
        walletRepository.save(wallet);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        BigDecimal debitAmount = new BigDecimal("80.00"); // 2 * 80 = 160 > 100

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger expectedFailureCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    walletService.executeDebit(
                        user.getId(),
                        debitAmount,
                        TransactionCategory.TRADE_BUY,
                        "ORDER-RACE-" + index,
                        "Race for balance"
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException | ObjectOptimisticLockingFailureException expected) {
                    expectedFailureCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads must finish within timeout");
        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: " + unexpectedErrors);
        assertEquals(1, successCount.get(), "Exactly one debit must succeed");
        assertEquals(1, expectedFailureCount.get(), "The other debit must fail");

        Wallet finalWallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("20.00").compareTo(finalWallet.getBalance()),
            "Final balance must be 20.00 (never negative)");
        assertTrue(finalWallet.getBalance().compareTo(BigDecimal.ZERO) >= 0,
            "Balance must never be negative");
    }

    @Test
    void getTransactionHistory_DateFiltering_RespectsHalfOpenInterval() throws Exception {
        User user = createTestUser("historydateuser");
        Instant t0 = Instant.now().minusSeconds(5);

        walletService.executeCredit(
            user.getId(), new BigDecimal("1000.00"), TransactionCategory.TOP_UP, "DATE-1", "First"
        );

        // Sleep briefly to ensure distinct millisecond timestamps
        Thread.sleep(20);
        Instant t1 = Instant.now();
        Thread.sleep(20);

        walletService.executeDebit(
            user.getId(), new BigDecimal("500.00"), TransactionCategory.TRADE_BUY, "DATE-2", "Second"
        );

        Instant t2 = Instant.now().plusSeconds(5);

        // Query with from=t0, to=t1: includes DATE-1, excludes DATE-2
        var pageBefore = walletService.getTransactionHistory(
            user.getUsername(), null, null, t0, t1, org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertTrue(pageBefore.getContent().stream().anyMatch(t -> "DATE-1".equals(t.getReferenceId())),
            "DATE-1 must be included in [t0, t1)");
        assertFalse(pageBefore.getContent().stream().anyMatch(t -> "DATE-2".equals(t.getReferenceId())),
            "DATE-2 must be excluded from [t0, t1)");

        // Query with from=t1, to=t2: includes DATE-2, excludes DATE-1
        var pageAfter = walletService.getTransactionHistory(
            user.getUsername(), null, null, t1, t2, org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertTrue(pageAfter.getContent().stream().anyMatch(t -> "DATE-2".equals(t.getReferenceId())),
            "DATE-2 must be included in [t1, t2)");
        assertFalse(pageAfter.getContent().stream().anyMatch(t -> "DATE-1".equals(t.getReferenceId())),
            "DATE-1 must be excluded from [t1, t2)");
    }
}
