package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.auth.email-verification-required=true")
@DisplayName("Email Verification Enforcement Gate Tests")
class EmailVerificationRequiredIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;

    @BeforeEach
    @AfterEach
    void cleanup() {
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
    @DisplayName("When verification required=true: unverified new users get 403, verified & legacy users get 200")
    void enforcesVerificationOnlyForUnverifiedNewUsers() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("New Unverified");
        req.setUsername("unverified");
        req.setEmail("unv@example.com");
        req.setPassword("password123");
        req.setConfirmPassword("password123");
        userService.registerUser(req);

        // Unverified new account gets 403 after valid password
        mockMvc.perform(formLogin("/api/auth/login").user("unverified").password("password123"))
            .andExpect(status().isForbidden())
            .andExpect(unauthenticated());

        // Wrong password still returns 401
        mockMvc.perform(formLogin("/api/auth/login").user("unverified").password("wrongpass"))
            .andExpect(status().isUnauthorized());

        // Once verified, login succeeds
        User u = userRepository.findByUsername("unverified").orElseThrow();
        u.setEmailVerified(true);
        userRepository.saveAndFlush(u);
        mockMvc.perform(formLogin("/api/auth/login").user("unverified").password("password123"))
            .andExpect(status().isOk())
            .andExpect(authenticated().withUsername("unverified"));

        // Legacy user (emailVerified == null) can always log in
        userRepository.saveAndFlush(new User("Legacy", "legacy", "legacy@example.com", passwordEncoder.encode("password123")));
        mockMvc.perform(formLogin("/api/auth/login").user("legacy").password("password123"))
            .andExpect(status().isOk())
            .andExpect(authenticated().withUsername("legacy"));
    }
}
