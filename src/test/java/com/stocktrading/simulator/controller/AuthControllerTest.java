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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
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
}
