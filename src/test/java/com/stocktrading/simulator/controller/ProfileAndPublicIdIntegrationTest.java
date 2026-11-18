package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.config.PublicIdBackfillRunner;
import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Profile Management & Public User ID Integration Tests")
class ProfileAndPublicIdIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PublicIdBackfillRunner backfillRunner;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
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

    private void register(String name, String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","username":"%s","email":"%s","password":"%s","confirmPassword":"%s"}
                    """.formatted(name, username, email, password, password)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Register atomically creates user, unique 6-digit public ID, wallet, and initial ledger")
    void registerCreatesUserPublicIdWalletAndInitialLedger() throws Exception {
        Set<Integer> assignedIds = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            String uname = "reguser" + i;
            mockMvc.perform(post("/api/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"Reg %d","username":"%s","email":"%s@example.com",
                         "password":"password123","confirmPassword":"password123","publicUserId":100000}
                        """.formatted(i, uname, uname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicUserId").isNumber())
                .andExpect(jsonPath("$.virtualBalance").value(100000.00));

            User u = userRepository.findByUsername(uname).orElseThrow();
            assertThat(u.getPublicUserId()).isBetween(100_000, 999_999);
            assignedIds.add(u.getPublicUserId());

            Wallet w = walletRepository.findByUserId(u.getId()).orElseThrow();
            assertThat(w.getBalance()).isEqualByComparingTo("100000.00");

            var initTxn = walletTransactionRepository
                .findByWalletIdAndReferenceId(w.getId(), "INIT-" + u.getId()).orElseThrow();
            assertThat(initTxn.getCategory()).isEqualTo(TransactionCategory.INITIAL_BALANCE);
        }
        assertThat(assignedIds).hasSize(4);
    }

    @Test
    @DisplayName("PUT /api/user/me updates name, ignores protected fields, and returns REAL live wallet balance")
    void updateProfileReturnsRealWalletBalanceAndProtectsImmutableFields() throws Exception {
        register("Alice", "alice", "alice@example.com", "OldPassw0rd");
        User alice = userRepository.findByUsername("alice").orElseThrow();
        Integer originalPublicId = alice.getPublicUserId();

        // Mutate real wallet balance away from 100000.00 (e.g. down to 77500.00)
        Wallet w = walletRepository.findByUserId(alice.getId()).orElseThrow();
        w.debit(new BigDecimal("22500.00"));
        walletRepository.saveAndFlush(w);

        mockMvc.perform(put("/api/user/me").with(user("alice")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"  Alice Liddell ","publicUserId":111111,"username":"hacker",
                     "email":"evil@example.com","virtualBalance":999999}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alice Liddell"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.publicUserId").value(originalPublicId))
            .andExpect(jsonPath("$.virtualBalance").value(77500.00))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User reloaded = userRepository.findById(alice.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Alice Liddell");
        assertThat(reloaded.getPublicUserId()).isEqualTo(originalPublicId);
        assertThat(reloaded.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("PUT /api/user/me/password requires existing password and updates hash")
    void changePasswordRequiresCurrentPassword() throws Exception {
        register("Alice", "alice", "alice@example.com", "OldPassw0rd");

        // Wrong current password rejected
        mockMvc.perform(put("/api/user/me/password").with(user("alice")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"WrongPass1","newPassword":"NewPassw0rd!","confirmNewPassword":"NewPassw0rd!"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Current password is incorrect"));

        // Correct current password succeeds
        mockMvc.perform(put("/api/user/me/password").with(user("alice")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"OldPassw0rd","newPassword":"NewPassw0rd!","confirmNewPassword":"NewPassw0rd!"}
                    """))
            .andExpect(status().isNoContent());

        mockMvc.perform(formLogin("/api/auth/login").user("alice").password("NewPassw0rd!"))
            .andExpect(status().isOk())
            .andExpect(authenticated().withUsername("alice"));
        mockMvc.perform(formLogin("/api/auth/login").user("alice").password("OldPassw0rd"))
            .andExpect(status().isUnauthorized())
            .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("PublicIdBackfillRunner assigns unique IDs to legacy NULL users without altering modern users")
    void backfillFillsOnlyMissingIdsAndDatabaseEnforcesUniqueness() {
        User legacy = userRepository.saveAndFlush(new User("Legacy", "legacy", "legacy@example.com", passwordEncoder.encode("password123")));
        User modern = new User("Modern", "modern", "modern@example.com", passwordEncoder.encode("password123"));
        modern.setPublicUserId(543210);
        modern = userRepository.saveAndFlush(modern);

        assertThat(legacy.getPublicUserId()).isNull();

        backfillRunner.run(null);

        User reloadedLegacy = userRepository.findById(legacy.getId()).orElseThrow();
        assertThat(reloadedLegacy.getPublicUserId()).isBetween(100_000, 999_999);
        assertThat(reloadedLegacy.getName()).isEqualTo("Legacy");
        assertThat(userRepository.findById(modern.getId()).orElseThrow().getPublicUserId()).isEqualTo(543210);

        // Database unique constraint rejects duplicate publicUserId
        User dup = new User("Dup", "dupuser", "dup@example.com", "hash");
        dup.setPublicUserId(543210);
        assertThatThrownBy(() -> userRepository.saveAndFlush(dup))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Profile endpoints enforce authentication and CSRF")
    void profileEndpointsRequireAuthAndCsrf() throws Exception {
        mockMvc.perform(put("/api/user/me").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/user/me/password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"a\",\"newPassword\":\"12345678\",\"confirmNewPassword\":\"12345678\"}"))
            .andExpect(status().isUnauthorized());

        register("Alice", "alice", "alice@example.com", "password123");
        mockMvc.perform(put("/api/user/me").with(user("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
            .andExpect(status().isForbidden());
    }
}
