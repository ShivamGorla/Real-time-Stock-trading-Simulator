package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.model.AuthToken;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EmailAuthIntegrationTest.RecordingMailConfig.class)
@DisplayName("Email Verification & Password Recovery Integration Tests")
class EmailAuthIntegrationTest {

    @TestConfiguration
    static class RecordingMailConfig {
        @Bean
        RecordingMailSender javaMailSender() {
            return new RecordingMailSender();
        }
    }

    static class RecordingMailSender extends JavaMailSenderImpl {
        final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            sent.add(simpleMessage);
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            for (SimpleMailMessage m : simpleMessages) {
                sent.add(m);
            }
        }
    }

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private RecordingMailSender mail;
    @Autowired private UserService userService;
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
        mail.sent.clear();
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

    private String tokenFromLastMail(int expectedCount) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mail.sent.size() >= expectedCount);
        Matcher m = TOKEN.matcher(mail.sent.get(mail.sent.size() - 1).getText());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private String plantToken(User user, AuthToken.Type type, Instant expiresAt) throws Exception {
        String raw = "planted-" + type + "-" + System.nanoTime();
        String hash = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        authTokenRepository.saveAndFlush(new AuthToken(user, type, hash, Instant.now().minusSeconds(7200), expiresAt));
        return raw;
    }

    @Test
    @DisplayName("Verification token is stored hashed, verifies email once, and rejects replay")
    void registrationSendsVerificationEmailAndTokenIsSingleUse() throws Exception {
        registerUser("asha", "asha@example.com");
        assertThat(userRepository.findByUsername("asha").orElseThrow().getEmailVerified()).isFalse();

        String token = tokenFromLastMail(1);
        // Raw token must never be stored in DB
        assertThat(authTokenRepository.findAll()).noneMatch(t -> t.getTokenHash().equals(token));

        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
            .andExpect(status().isOk());
        assertThat(userRepository.findByUsername("asha").orElseThrow().getEmailVerified()).isTrue();

        // Single-use enforcement
        mockMvc.perform(get("/api/auth/verify-email").param("token", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("This link has already been used."));
    }

    @Test
    @DisplayName("Password reset tokens expire, are single-use, cannot cross token types, and update password")
    void passwordResetTokenExpirySingleUseAndCrossTypeProtection() throws Exception {
        User alice = registerUser("alice", "alice@example.com");
        tokenFromLastMail(1); // consume verification email
        mail.sent.clear();

        // Forgot password returns identical generic message for unknown vs known email
        String unknownResp = mockMvc.perform(post("/api/auth/forgot-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.com\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        String knownResp = mockMvc.perform(post("/api/auth/forgot-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alice@example.com\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        assertThat(knownResp).isEqualTo(unknownResp);

        String resetToken = tokenFromLastMail(1);

        // Reset token cannot verify email
        mockMvc.perform(get("/api/auth/verify-email").param("token", resetToken))
            .andExpect(status().isBadRequest());

        // Expired reset token rejected
        String expired = plantToken(alice, AuthToken.Type.RESET_PASSWORD, Instant.now().minusSeconds(10));
        mockMvc.perform(post("/api/auth/reset-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + expired + "\",\"newPassword\":\"BrandNew123\",\"confirmPassword\":\"BrandNew123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("This link has expired. Request a new one."));

        // Valid reset succeeds once
        String resetPayload = "{\"token\":\"" + resetToken + "\",\"newPassword\":\"BrandNew123\",\"confirmPassword\":\"BrandNew123\"}";
        mockMvc.perform(post("/api/auth/reset-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(resetPayload))
            .andExpect(status().isOk());

        mockMvc.perform(formLogin("/api/auth/login").user("alice").password("BrandNew123")).andExpect(status().isOk());
        mockMvc.perform(formLogin("/api/auth/login").user("alice").password("password123")).andExpect(status().isUnauthorized());

        // Replay of reset token rejected
        mockMvc.perform(post("/api/auth/reset-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(resetPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("This link has already been used."));
    }
}
