package com.stocktrading.simulator.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("API Security Allowlist & Default-Deny Regression Tests")
class SecurityPolicyRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Explicit public pages, static assets, health, and stock catalog GET endpoints remain accessible")
    void publicRoutesRemainAccessible() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/login.html")).andExpect(status().isOk());
        mockMvc.perform(get("/register.html")).andExpect(status().isOk());
        mockMvc.perform(get("/forgot-password.html")).andExpect(status().isOk());
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/auth.js")).andExpect(status().isOk());
        mockMvc.perform(get("/js/profile.js")).andExpect(status().isOk());
        mockMvc.perform(get("/js/assistant.js")).andExpect(status().isOk());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/stocks")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("All protected user, wallet, portfolio, payment, order, trade, and report endpoints reject unauthenticated requests with 401")
    void protectedApiEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/user/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wallet")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wallet/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/trades")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portfolio")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/portfolio")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/trading-history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/assistant/conversations/test-conv-1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/assistant/chat")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hi\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Catch-all public access is closed: unknown /api/** and non-GET /api/stocks/** require authentication")
    void unknownAndFutureRoutesDefaultToAuthenticated() throws Exception {
        mockMvc.perform(get("/api/internal/secret")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/future-feature")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/stocks/mutate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
