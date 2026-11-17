package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.exception.InsufficientFundsException;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.repository.WalletRepository;
import com.stocktrading.simulator.repository.WalletTransactionRepository;
import com.stocktrading.simulator.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JpaWalletAdapterTest {

    @Autowired
    private JpaWalletAdapter jpaWalletAdapter;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private com.stocktrading.simulator.repository.OrderRepository orderRepository;

    @Autowired
    private com.stocktrading.simulator.repository.TradeRepository tradeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private User testUser1;
    private User testUser2;

    @org.junit.jupiter.api.AfterEach
    void teardown() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void setup() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        testUser1 = userRepository.save(new User("User One", "userone", "one@example.com", "hash1"));
        testUser2 = userRepository.save(new User("User Two", "usertwo", "two@example.com", "hash2"));

        walletService.createWalletWithInitialBalance(testUser1);
        walletService.createWalletWithInitialBalance(testUser2);
    }

    @Test
    void debitSufficientFunds_ValidBuyOrder_DecreasesBalanceAndRecordsTradeBuyLedger() {
        Long orderId = 101L;
        BigDecimal debitAmount = new BigDecimal("25000.00");

        transactionTemplate.execute(status -> {
            jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), debitAmount, orderId, "Buy 10 INFY shares");
            return null;
        });

        Wallet wallet = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("75000.00").compareTo(wallet.getBalance()),
                "Wallet balance must be exactly deducted by debit amount");

        Optional<WalletTransaction> txnOpt =
                walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), "ORDER-BUY-" + orderId);
        assertTrue(txnOpt.isPresent(), "TRADE_BUY ledger entry must be created");

        WalletTransaction txn = txnOpt.get();
        assertEquals(TransactionType.DEBIT, txn.getType());
        assertEquals(TransactionCategory.TRADE_BUY, txn.getCategory());
        assertEquals(0, debitAmount.compareTo(txn.getAmount()));
        assertEquals(0, new BigDecimal("75000.00").compareTo(txn.getBalanceAfter()));
        assertEquals("INR", txn.getCurrency());
        assertEquals("ORDER-BUY-101", txn.getReferenceId());
        assertEquals("Buy 10 INFY shares", txn.getDescription());
    }

    @Test
    void creditFunds_ValidSellOrder_IncreasesBalanceAndRecordsTradeSellLedger() {
        Long orderId = 202L;
        BigDecimal creditAmount = new BigDecimal("15000.50");

        transactionTemplate.execute(status -> {
            jpaWalletAdapter.creditFunds(testUser1.getId(), creditAmount, orderId, "Sell 5 INFY shares");
            return null;
        });

        Wallet wallet = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("115000.50").compareTo(wallet.getBalance()),
                "Wallet balance must be credited");

        Optional<WalletTransaction> txnOpt =
                walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), "ORDER-SELL-" + orderId);
        assertTrue(txnOpt.isPresent(), "TRADE_SELL ledger entry must be created");

        WalletTransaction txn = txnOpt.get();
        assertEquals(TransactionType.CREDIT, txn.getType());
        assertEquals(TransactionCategory.TRADE_SELL, txn.getCategory());
        assertEquals(0, creditAmount.compareTo(txn.getAmount()));
        assertEquals(0, new BigDecimal("115000.50").compareTo(txn.getBalanceAfter()));
        assertEquals("ORDER-SELL-202", txn.getReferenceId());
        assertEquals("Sell 5 INFY shares", txn.getDescription());
    }

    @Test
    void debitSufficientFunds_InsufficientFunds_ThrowsInsufficientFundsException_AndRollsBack() {
        Long orderId = 303L;
        BigDecimal excessiveAmount = new BigDecimal("150000.00"); // balance is 100,000

        assertThrows(InsufficientFundsException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), excessiveAmount, orderId, "Excessive buy");
                return null;
            })
        );

        Wallet wallet = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet.getBalance()),
                "Wallet balance must remain unchanged after failed debit");

        Optional<WalletTransaction> txnOpt =
                walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), "ORDER-BUY-" + orderId);
        assertFalse(txnOpt.isPresent(), "No ledger transaction should exist after rollback");
    }

    @Test
    void debitAndCredit_CalledWithoutActiveTransaction_ThrowsIllegalTransactionStateException() {
        // Enforces Propagation.MANDATORY
        assertThrows(IllegalTransactionStateException.class, () ->
            jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), new BigDecimal("100.00"), 401L, "Ref")
        );

        assertThrows(IllegalTransactionStateException.class, () ->
            jpaWalletAdapter.creditFunds(testUser1.getId(), new BigDecimal("100.00"), 402L, "Ref")
        );
    }

    @Test
    void duplicateOrderReferenceId_ThrowsDataIntegrityViolation_AndPreventsDoubleOperation() {
        Long orderId = 505L;
        BigDecimal amount = new BigDecimal("1000.00");

        // First debit succeeds
        transactionTemplate.execute(status -> {
            jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), amount, orderId, "First");
            return null;
        });

        // Replaying with identical orderId in a second transaction throws DataIntegrityViolationException on (wallet_id, reference_id)
        assertThrows(DataIntegrityViolationException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), amount, orderId, "Duplicate");
                return null;
            })
        );

        Wallet wallet = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("99000.00").compareTo(wallet.getBalance()),
                "Wallet must only be debited once for same order reference");
    }

    @Test
    void monetaryValidation_RejectsInvalidAmountsAndOverflows() {
        // Amount below minimum
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), new BigDecimal("0.00"), 1L, "Zero");
                return null;
            })
        );

        // Amount with excessive scale (> 2 decimal places)
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), new BigDecimal("10.005"), 1L, "Scale");
                return null;
            })
        );

        // Amount exceeding DECIMAL(15,2) limit (9999999999999.99)
        assertThrows(IllegalStateException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), new BigDecimal("10000000000000.00"), 1L, "Overflow");
                return null;
            })
        );
    }

    @Test
    void creditFunds_ResultingBalanceExceedsMaxDecimal15_ThrowsIllegalStateException() {
        // Set balance near maximum limit
        Wallet wallet = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        wallet.setBalance(new BigDecimal("9999999999990.00"));
        walletRepository.save(wallet);

        // Credit 20.00 -> resulting balance 10,000,000,000,010.00 which exceeds DECIMAL(15,2) ceiling (9999999999999.99)
        assertThrows(IllegalStateException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.creditFunds(testUser1.getId(), new BigDecimal("20.00"), 601L, "Overflow credit");
                return null;
            })
        );

        Wallet walletAfter = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("9999999999990.00").compareTo(walletAfter.getBalance()),
                "Balance must remain unchanged when credit would overflow maximum decimal limit");
    }

    @Test
    void operations_WhenUserHasNoWallet_ThrowsIllegalStateException() {
        User userWithoutWallet = userRepository.save(new User("No Wallet", "nowallet", "no@example.com", "hash"));

        assertThrows(IllegalStateException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.debitSufficientFunds(userWithoutWallet.getId(), new BigDecimal("100.00"), 701L, "Ref");
                return null;
            })
        );

        assertThrows(IllegalStateException.class, () ->
            transactionTemplate.execute(status -> {
                jpaWalletAdapter.creditFunds(userWithoutWallet.getId(), new BigDecimal("100.00"), 702L, "Ref");
                return null;
            })
        );
    }

    @Test
    void walletOwnershipIsolation_DebitOnOneUserDoesNotAffectAnother() {
        transactionTemplate.execute(status -> {
            jpaWalletAdapter.debitSufficientFunds(testUser1.getId(), new BigDecimal("30000.00"), 801L, "User 1 buy");
            return null;
        });

        Wallet wallet1 = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        Wallet wallet2 = walletRepository.findByUserId(testUser2.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("70000.00").compareTo(wallet1.getBalance()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet2.getBalance()),
                "User 2's wallet balance must remain untouched");
    }

    @Test
    void optimisticLocking_DetectsConcurrentWalletModifications() {
        Wallet wallet1 = walletRepository.findByUserId(testUser1.getId()).orElseThrow();
        Wallet wallet2 = walletRepository.findByUserId(testUser1.getId()).orElseThrow();

        // Transaction A updates wallet
        transactionTemplate.execute(status -> {
            wallet1.debit(new BigDecimal("1000.00"));
            walletRepository.saveAndFlush(wallet1);
            return null;
        });

        // Transaction B attempts to save stale wallet instance
        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
            transactionTemplate.execute(status -> {
                wallet2.debit(new BigDecimal("2000.00"));
                walletRepository.saveAndFlush(wallet2);
                return null;
            })
        );
    }
}
