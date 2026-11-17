package com.stocktrading.simulator.service;

import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.TransactionType;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.model.WalletTransaction;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WalletServiceTest {

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

    @BeforeEach
    @AfterEach
    void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createWalletWithInitialBalance_CreatesWalletAndLedgerEntryWith100000INR() {
        User user = new User("Alice Smith", "alicesmith", "alice@example.com", "hash123");
        user = userRepository.save(user);

        Wallet wallet = walletService.createWalletWithInitialBalance(user);

        assertNotNull(wallet.getId());
        assertEquals(user.getId(), wallet.getUser().getId());
        assertEquals(new BigDecimal("100000.00"), wallet.getBalance());
        assertEquals("INR", wallet.getCurrency());
        assertNotNull(wallet.getVersion());

        // Verify initial ledger entry
        Optional<WalletTransaction> txnOpt =
                walletTransactionRepository.findByWalletIdAndReferenceId(wallet.getId(), "INIT-" + user.getId());
        assertTrue(txnOpt.isPresent(), "Initial ledger transaction must exist");
        WalletTransaction txn = txnOpt.get();
        assertEquals(TransactionType.CREDIT, txn.getType());
        assertEquals(TransactionCategory.INITIAL_BALANCE, txn.getCategory());
        assertEquals(0, new BigDecimal("100000.00").compareTo(txn.getAmount()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(txn.getBalanceAfter()));
        assertEquals("INR", txn.getCurrency());
        assertEquals("INIT-" + user.getId(), txn.getReferenceId());
        assertNotNull(txn.getCreatedAt());
    }

    @Test
    void createWalletWithInitialBalance_WhenWalletAlreadyExists_ThrowsIllegalStateException() {
        User user = new User("Bob Jones", "bobjones", "bob@example.com", "hash123");
        User savedUser = userRepository.save(user);

        walletService.createWalletWithInitialBalance(savedUser);

        assertThrows(IllegalStateException.class, () -> walletService.createWalletWithInitialBalance(savedUser));
    }

    @Test
    void getWalletByUsername_ExistingUserWithWallet_ReturnsCorrectWallet() {
        User user = new User("Charlie Brown", "charlie", "charlie@example.com", "hash123");
        user = userRepository.save(user);
        Wallet wallet = walletRepository.save(new Wallet(user, new BigDecimal("75000.50"), "INR"));

        Wallet result = walletService.getWalletByUsername("charlie");

        assertEquals(wallet.getId(), result.getId());
        assertEquals(new BigDecimal("75000.50"), result.getBalance());
        assertEquals("charlie", result.getUser().getUsername());
    }

    @Test
    void existingWalletBalance_IsNotResetOrModified() {
        User user = new User("Daisy Ridley", "daisy", "daisy@example.com", "hash123");
        user = userRepository.save(user);
        Wallet initialWallet = walletRepository.save(new Wallet(user, new BigDecimal("42000.00"), "INR"));

        Wallet retrieved = walletService.getWalletByUsername("daisy");

        assertEquals(new BigDecimal("42000.00"), retrieved.getBalance());
        assertEquals(initialWallet.getId(), retrieved.getId());
    }

    @Test
    void getWalletByUsername_UserWithoutWallet_ThrowsIllegalStateException() {
        User user = new User("Eve Adams", "eveadams", "eve@example.com", "hash123");
        userRepository.save(user);

        assertThrows(IllegalStateException.class, () -> walletService.getWalletByUsername("eveadams"));
    }
}
