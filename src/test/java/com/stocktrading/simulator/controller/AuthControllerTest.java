package com.stocktrading.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.stocktrading.simulator.repository.WalletRepository walletRepository;

    @Autowired
    private com.stocktrading.simulator.repository.WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private com.stocktrading.simulator.repository.OrderRepository orderRepository;

    @Autowired
    private com.stocktrading.simulator.repository.TradeRepository tradeRepository;

    @Autowired
    private com.stocktrading.simulator.repository.HoldingRepository holdingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_ValidRequest_Success() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Test User");
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("password123");
        req.setConfirmPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.virtualBalance").value(100000.0));

        assertTrue(userRepository.existsByUsername("testuser"));
        User createdUser = userRepository.findByUsername("testuser").orElseThrow();
        var walletOpt = walletRepository.findByUserId(createdUser.getId());
        assertTrue(walletOpt.isPresent());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("100000.00"), walletOpt.get().getBalance());
        org.junit.jupiter.api.Assertions.assertEquals("INR", walletOpt.get().getCurrency());

        var initTxnOpt = walletTransactionRepository.findByWalletIdAndReferenceId(walletOpt.get().getId(), "INIT-" + createdUser.getId());
        assertTrue(initTxnOpt.isPresent(), "Initial wallet transaction must be created on user registration");
        org.junit.jupiter.api.Assertions.assertEquals(com.stocktrading.simulator.model.TransactionCategory.INITIAL_BALANCE, initTxnOpt.get().getCategory());
        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("100000.00").compareTo(initTxnOpt.get().getAmount()));
        org.junit.jupiter.api.Assertions.assertEquals(0, new java.math.BigDecimal("100000.00").compareTo(initTxnOpt.get().getBalanceAfter()));
        org.junit.jupiter.api.Assertions.assertEquals(1, walletTransactionRepository.findAll().size(), "Exactly one initial transaction ledger entry must exist");
    }

    @Test
    void register_DuplicateUsername_ReturnsBadRequest() throws Exception {
        User user = new User("Existing", "testuser", "old@example.com", "hash");
        userRepository.save(user);

        RegisterRequest req = new RegisterRequest();
        req.setName("Test User");
        req.setUsername("testuser");
        req.setEmail("new@example.com");
        req.setPassword("password123");
        req.setConfirmPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void login_ValidCredentials_Success() throws Exception {
        User user = new User("Login Test", "logintest", "login@example.com", passwordEncoder.encode("password123"));
        userRepository.save(user);

        mockMvc.perform(formLogin("/api/auth/login")
                .user("logintest").password("password123"))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername("logintest"));
    }

    @Test
    void login_InvalidCredentials_Fails() throws Exception {
        mockMvc.perform(formLogin("/api/auth/login")
                .user("wrong").password("wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());
    }

    @Test
    void dashboard_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "meuser")
    void dashboard_Authenticated_ReturnsLiveBalanceAndTracksSubsequentChanges() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Me User");
        req.setUsername("meuser");
        req.setEmail("me@example.com");
        req.setPassword("password123");
        req.setConfirmPassword("password123");

        // 1. Registration response shows 100000.00 INR
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.virtualBalance").value(100000.00));

        // 2. GET /api/user/me shows the same balance
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("meuser"))
                .andExpect(jsonPath("$.virtualBalance").value(100000.00));

        // 3. The INITIAL_BALANCE ledger entry exists exactly once
        User user = userRepository.findByUsername("meuser").orElseThrow();
        var txns = walletTransactionRepository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(1, txns.size());
        org.junit.jupiter.api.Assertions.assertEquals(com.stocktrading.simulator.model.TransactionCategory.INITIAL_BALANCE, txns.get(0).getCategory());
        org.junit.jupiter.api.Assertions.assertEquals("INIT-" + user.getId(), txns.get(0).getReferenceId());

        // 4. Subsequent balance changes are reflected by GET /api/user/me
        var wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
        wallet.debit(new java.math.BigDecimal("15000.00"));
        walletRepository.saveAndFlush(wallet);

        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("meuser"))
                .andExpect(jsonPath("$.virtualBalance").value(85000.00));
    }
}
