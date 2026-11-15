package com.stocktrading.simulator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.StockPriceHistoryRepository;
import com.stocktrading.simulator.repository.StockRepository;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        orderRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User("Test User", "testuser", "test@example.com", "hash"));
        userRepository.save(new User("Other User", "otheruser", "other@example.com", "hash"));

        stockService.createStockIfAbsent("INFY", "Infosys Ltd", new BigDecimal("1500.00"));
    }

    @Test
    void unauthenticated_OrderEndpoints_Return401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderRequest(UUID.randomUUID().toString(), "INFY", 5))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void placeBuyOrder_MissingCsrf_Returns403() throws Exception {
        OrderRequest request = new OrderRequest(UUID.randomUUID().toString(), "INFY", 5);

        mockMvc.perform(post("/api/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser")
    void placeBuyOrder_ValidRequest_Returns201Created() throws Exception {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "INFY", 10);

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.clientOrderId").value(clientOrderId))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.symbol").value("INFY"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.quotedPrice").value(1500.00))
                .andExpect(jsonPath("$.totalQuotedAmount").value(15000.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.executionPrice").doesNotExist())
                .andExpect(jsonPath("$.executedAt").doesNotExist())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void placeSellOrder_ValidRequest_Returns201Created() throws Exception {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request = new OrderRequest(clientOrderId, "INFY", 2);

        mockMvc.perform(post("/api/orders/sell")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void placeOrder_DuplicateClientOrderId_Returns409Conflict() throws Exception {
        String clientOrderId = UUID.randomUUID().toString();
        OrderRequest request1 = new OrderRequest(clientOrderId, "INFY", 2);
        OrderRequest request2 = new OrderRequest(clientOrderId, "INFY", 3);

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void placeOrder_InvalidQuantity_Returns400BadRequest() throws Exception {
        OrderRequest request = new OrderRequest(UUID.randomUUID().toString(), "INFY", 0);

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser")
    void placeOrder_UnknownSymbol_Returns404NotFound() throws Exception {
        OrderRequest request = new OrderRequest(UUID.randomUUID().toString(), "UNKNOWN", 5);

        mockMvc.perform(post("/api/orders/buy")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("UNKNOWN")));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserOrders_ReturnsOnlyUserOrders() throws Exception {
        OrderRequest req1 = new OrderRequest(UUID.randomUUID().toString(), "INFY", 1);
        OrderRequest req2 = new OrderRequest(UUID.randomUUID().toString(), "INFY", 2);

        mockMvc.perform(post("/api/orders/buy").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req1)));
        mockMvc.perform(post("/api/orders/sell").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req2)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "otheruser")
    void getOrderById_NotOwnedByUser_Returns404WithoutLeaking() throws Exception {
        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("not found")));
    }
}
