package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.TransactionType;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.model.WalletTransaction;
import com.stocktrading.simulator.port.WalletTopUpPort;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("JpaWalletTopUpAdapter Integration Tests")
class JpaWalletTopUpAdapterTest {

    @Autowired
    private WalletTopUpPort walletTopUpPort;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private User testUser;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        cleanup();
        testUser = userRepository.save(new User("TopUp Adapter User", "topupuser", "topup@example.com", "hash"));
        testWallet = walletService.createWalletWithInitialBalance(testUser);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        authTokenRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Must reject invocation outside an active database transaction (Propagation.MANDATORY)")
    void creditTopUp_WithoutTransaction_ThrowsIllegalTransactionStateException() {
        assertThrows(IllegalTransactionStateException.class, () ->
            walletTopUpPort.creditTopUp(testUser.getId(), new BigDecimal("500.00"), "TOPUP-NO-TX")
        );
    }

    @Test
    @DisplayName("Credits persistent wallet and inserts exactly one TOP_UP ledger entry")
    void creditTopUp_ValidTransaction_CreditsWalletAndCreatesTopUpLedgerEntry() {
        tx.executeWithoutResult(status ->
            walletTopUpPort.creditTopUp(testUser.getId(), new BigDecimal("2500.50"), "TOPUP-101")
        );

        Wallet reloaded = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("102500.50").compareTo(reloaded.getBalance()));

        WalletTransaction txn = walletTransactionRepository
            .findByWalletIdAndReferenceId(testWallet.getId(), "TOPUP-101")
            .orElseThrow();
        assertEquals(TransactionType.CREDIT, txn.getType());
        assertEquals(TransactionCategory.TOP_UP, txn.getCategory());
        assertEquals(0, new BigDecimal("2500.50").compareTo(txn.getAmount()));
        assertEquals(0, new BigDecimal("102500.50").compareTo(txn.getBalanceAfter()));
    }

    @Test
    @DisplayName("Duplicate paymentReference is blocked by unique ledger constraint and rolls back wallet credit")
    void creditTopUp_DuplicateReference_RollsBackWalletCredit() {
        tx.executeWithoutResult(status ->
            walletTopUpPort.creditTopUp(testUser.getId(), new BigDecimal("1000.00"), "TOPUP-DUP-1")
        );

        assertThrows(DataIntegrityViolationException.class, () ->
            tx.executeWithoutResult(status ->
                walletTopUpPort.creditTopUp(testUser.getId(), new BigDecimal("1000.00"), "TOPUP-DUP-1")
            )
        );

        Wallet reloaded = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("101000.00").compareTo(reloaded.getBalance()));
    }

    @Test
    @DisplayName("Prevents wallet balance overflow beyond DECIMAL(15,2) maximum")
    void creditTopUp_ExceedsMaxWalletDecimal_ThrowsIllegalStateAndRollsBack() {
        tx.executeWithoutResult(status -> {
            Wallet w = walletRepository.findByUserId(testUser.getId()).orElseThrow();
            w.credit(JpaWalletAdapter.MAX_WALLET_DECIMAL.subtract(w.getBalance()).subtract(new BigDecimal("50.00")));
            walletRepository.saveAndFlush(w);
        });

        assertThrows(IllegalStateException.class, () ->
            tx.executeWithoutResult(status ->
                walletTopUpPort.creditTopUp(testUser.getId(), new BigDecimal("100.00"), "TOPUP-OVERFLOW")
            )
        );

        assertTrue(walletTransactionRepository.findByWalletIdAndReferenceId(testWallet.getId(), "TOPUP-OVERFLOW").isEmpty());
    }
}
