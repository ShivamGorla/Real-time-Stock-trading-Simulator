package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.adapter.JpaWalletAdapter;
import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.port.WalletTopUpPort;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.PaymentService;
import com.stocktrading.simulator.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Simulated Top-Up HTTP Integration Tests (Real Wallet & Ledger)")
class PaymentTopUpIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        cleanup();
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

    private User registerUser(String username, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setName("User " + username);
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword("password123");
        req.setConfirmPassword("password123");
        userService.registerUser(req);
        return userRepository.findByUsername(username).orElseThrow();
    }

    private MockHttpServletRequestBuilder topUp(String username, String key, String amountJson) {
        MockHttpServletRequestBuilder b = post("/api/payments/top-up")
            .with(user(username))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":" + amountJson + "}");
        return key == null ? b : b.header("Idempotency-Key", key);
    }

    @Test
    @DisplayName("Successful top-up credits persistent wallet, creates one TOP_UP ledger entry, and updates /api/user/me")
    void successfulTopUpCreditsPersistentWalletAndLedger() throws Exception {
        User alice = registerUser("alice", "alice@example.com");

        mockMvc.perform(topUp("alice", "key-topup-001", "5000.00"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.simulated").value(true))
            .andExpect(jsonPath("$.amount").value(5000.00));

        Wallet wallet = walletRepository.findByUserId(alice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("105000.00").compareTo(wallet.getBalance()));

        List<WalletTransaction> txns = walletTransactionRepository
            .findByWalletId(wallet.getId(), Pageable.unpaged()).getContent();
        long topUpTxns = txns.stream().filter(t -> t.getCategory() == TransactionCategory.TOP_UP).count();
        assertEquals(1, topUpTxns);

        WalletTransaction topUpTxn = txns.stream()
            .filter(t -> t.getCategory() == TransactionCategory.TOP_UP)
            .findFirst().orElseThrow();
        assertEquals(TransactionType.CREDIT, topUpTxn.getType());
        assertEquals(0, new BigDecimal("5000.00").compareTo(topUpTxn.getAmount()));
        assertEquals(0, new BigDecimal("105000.00").compareTo(topUpTxn.getBalanceAfter()));
        assertTrue(topUpTxn.getReferenceId().startsWith("TOPUP-"));

        // GET /api/user/me shows the real credited wallet balance (never double-counted)
        mockMvc.perform(get("/api/user/me").with(user("alice")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.virtualBalance").value(105000.00));
    }

    @Test
    @DisplayName("Repeated Idempotency-Key returns same payment and does not credit twice")
    void repeatedIdempotencyKeyCreditsOnceAndRejectsMismatchedAmount() throws Exception {
        User alice = registerUser("alice", "alice@example.com");

        String first = mockMvc.perform(topUp("alice", "key-repeat-001", "1500.00"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(topUp("alice", "key-repeat-001", "1500.00"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertEquals(first, second);

        Wallet wallet = walletRepository.findByUserId(alice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("101500.00").compareTo(wallet.getBalance()));
        assertEquals(1, paymentTransactionRepository.count());

        // Same key with different amount must fail with HTTP 400
        mockMvc.perform(topUp("alice", "key-repeat-001", "2000.00"))
            .andExpect(status().isBadRequest());
        Wallet reloaded = walletRepository.findByUserId(alice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("101500.00").compareTo(reloaded.getBalance()));
    }

    @Test
    @DisplayName("Concurrent top-up requests with same Idempotency-Key credit exactly once")
    void concurrentTopUpSameKeyCreditsOnce() throws Exception {
        User alice = registerUser("alice", "alice@example.com");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                results.add(pool.submit(() -> {
                    latch.await();
                    return mockMvc.perform(topUp("alice", "key-race-00001", "2000.00"))
                        .andReturn().getResponse().getStatus();
                }));
            }
            latch.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get(10, TimeUnit.SECONDS)).isLessThan(500);
            }
        } finally {
            pool.shutdownNow();
        }

        Wallet wallet = walletRepository.findByUserId(alice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("102000.00").compareTo(wallet.getBalance()));
        assertEquals(1, paymentTransactionRepository.count());
        assertEquals(PaymentTransaction.Status.SUCCESS, paymentTransactionRepository.findAll().get(0).getStatus());

        long topUpLedgerEntries = walletTransactionRepository
            .findByWalletId(wallet.getId(), Pageable.unpaged()).getContent().stream()
            .filter(t -> t.getCategory() == TransactionCategory.TOP_UP)
            .count();
        assertEquals(1, topUpLedgerEntries);
    }

    @Test
    @DisplayName("Declined top-up (666.66) records FAILED payment and credits nothing")
    void declinedTopUpCreditsNothing() throws Exception {
        User alice = registerUser("alice", "alice@example.com");

        mockMvc.perform(topUp("alice", "key-decline-01", "666.66"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureReason").exists());

        Wallet wallet = walletRepository.findByUserId(alice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100000.00").compareTo(wallet.getBalance()));

        long topUpLedgerEntries = walletTransactionRepository
            .findByWalletId(wallet.getId(), Pageable.unpaged()).getContent().stream()
            .filter(t -> t.getCategory() == TransactionCategory.TOP_UP)
            .count();
        assertEquals(0, topUpLedgerEntries);
    }

    @Test
    @DisplayName("Wallet mutation failure (overflow) rolls back payment SUCCESS transition leaving payment PENDING")
    void walletMutationFailureRollsBackPaymentStatusToPending() throws Exception {
        User alice = registerUser("alice", "alice@example.com");

        // Move wallet balance within 10 INR of DECIMAL(15,2) maximum
        tx.executeWithoutResult(status -> {
            Wallet w = walletRepository.findByUserId(alice.getId()).orElseThrow();
            w.credit(JpaWalletAdapter.MAX_WALLET_DECIMAL.subtract(w.getBalance()).subtract(new BigDecimal("10.00")));
            walletRepository.saveAndFlush(w);
        });

        // Top-up of 500.00 exceeds DECIMAL(15,2) -> JpaWalletTopUpAdapter throws IllegalStateException (HTTP 409)
        mockMvc.perform(topUp("alice", "key-overflow-1", "500.00"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").exists());

        // Payment row must remain PENDING (SUCCESS transition rolled back atomically)
        PaymentTransaction p = paymentTransactionRepository
            .findByUserAndIdempotencyKey(alice, "key-overflow-1").orElseThrow();
        assertEquals(PaymentTransaction.Status.PENDING, p.getStatus());

        Wallet wallet = walletRepository.findByUserId(alice.getId()).orElseThrow();
        long topUpLedgerEntries = walletTransactionRepository
            .findByWalletId(wallet.getId(), Pageable.unpaged()).getContent().stream()
            .filter(t -> t.getCategory() == TransactionCategory.TOP_UP)
            .count();
        assertEquals(0, topUpLedgerEntries);
    }

    @Test
    @DisplayName("Missing WalletTopUpPort fails closed with SettlementUnavailableException rather than faking SUCCESS")
    void missingWalletTopUpPortFailsClosed() {
        registerUser("alice", "alice@example.com");

        @SuppressWarnings("unchecked")
        ObjectProvider<WalletTopUpPort> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);

        PaymentService serviceWithoutPort = new PaymentService(
            userRepository, paymentTransactionRepository, emptyProvider, transactionManager,
            new BigDecimal("100"), new BigDecimal("500000"), new BigDecimal("666.66")
        );

        assertThrows(com.stocktrading.simulator.exception.SettlementUnavailableException.class, () ->
            serviceWithoutPort.topUp("alice", "key-missing-01", new BigDecimal("500.00"))
        );
        assertEquals(0, paymentTransactionRepository.count());
    }

    @Test
    @DisplayName("Payment endpoints require login, CSRF, and enforce strict user ownership")
    void authCsrfAndUserScopingEnforced() throws Exception {
        mockMvc.perform(post("/api/payments/top-up").with(csrf()).header("Idempotency-Key", "key-anon-0001")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/1")).andExpect(status().isUnauthorized());

        User alice = registerUser("alice", "alice@example.com");
        registerUser("bob", "bob@example.com");

        // Missing CSRF
        mockMvc.perform(post("/api/payments/top-up").with(user("alice")).header("Idempotency-Key", "key-nocsrf-01")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(topUp("alice", "key-alice-001", "500.00")).andExpect(status().isOk());
        mockMvc.perform(topUp("bob", "key-bob-00001", "700.00")).andExpect(status().isOk());

        long alicePaymentId = paymentTransactionRepository
            .findByUserAndIdempotencyKey(alice, "key-alice-001").orElseThrow().getId();

        mockMvc.perform(get("/api/payments/history").with(user("alice")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].amount").value(500.00));
        mockMvc.perform(get("/api/payments/" + alicePaymentId).with(user("alice")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/payments/" + alicePaymentId).with(user("bob")))
            .andExpect(status().isNotFound());
    }
}
