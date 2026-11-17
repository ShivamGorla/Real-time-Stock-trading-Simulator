package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.dto.UserResponse;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.model.WalletTransaction;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.repository.WalletRepository;
import com.stocktrading.simulator.repository.WalletTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @SpyBean
    private WalletRepository walletRepository;

    @Autowired
    private com.stocktrading.simulator.repository.OrderRepository orderRepository;

    @Autowired
    private com.stocktrading.simulator.repository.TradeRepository tradeRepository;

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
    void registerUser_Success_PersistsBothUserAndWalletInH2() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Integration User");
        req.setUsername("integrationuser");
        req.setEmail("integration@example.com");
        req.setPassword("password123");
        req.setConfirmPassword("password123");

        UserResponse res = userService.registerUser(req);

        assertNotNull(res);
        assertEquals(new BigDecimal("100000.00"), res.getVirtualBalance());

        Optional<User> userOpt = userRepository.findByUsername("integrationuser");
        assertTrue(userOpt.isPresent(), "User must be persisted in H2 database");

        Optional<Wallet> walletOpt = walletRepository.findByUserId(userOpt.get().getId());
        assertTrue(walletOpt.isPresent(), "Wallet must be persisted in H2 database");
        assertEquals(new BigDecimal("100000.00"), walletOpt.get().getBalance());
        assertEquals("INR", walletOpt.get().getCurrency());

        Optional<WalletTransaction> txnOpt =
                walletTransactionRepository.findByWalletIdAndReferenceId(walletOpt.get().getId(), "INIT-" + userOpt.get().getId());
        assertTrue(txnOpt.isPresent(), "Initial ledger transaction must be persisted in H2 database");
        assertEquals(0, new BigDecimal("100000.00").compareTo(txnOpt.get().getAmount()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(txnOpt.get().getBalanceAfter()));
    }

    @Test
    void registerUser_WalletCreationFailure_RollsBackUserRegistration() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Rollback Candidate");
        req.setUsername("rollbackuser");
        req.setEmail("rollback@example.com");
        req.setPassword("password123");
        req.setConfirmPassword("password123");

        // Force a controlled database persistence failure on wallet save
        doThrow(new DataIntegrityViolationException("Simulated wallet constraint failure"))
                .when(walletRepository).save(any(Wallet.class));

        // Attempting registration must throw an exception
        assertThrows(DataIntegrityViolationException.class, () -> userService.registerUser(req));

        // Verify that transaction rollback cleanly discarded the User row
        assertFalse(userRepository.existsByUsername("rollbackuser"),
                "User must NOT remain persisted in database after wallet creation fails");
        assertEquals(0, walletRepository.count(),
                "No wallet must remain persisted in database after rollback");
        assertEquals(0, walletTransactionRepository.count(),
                "No wallet transaction must remain persisted in database after rollback");
    }
}
