package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.StockPriceHistoryRepository;
import com.stocktrading.simulator.repository.StockRepository;
import com.stocktrading.simulator.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private StockService stockService;

    @BeforeEach
    void setup() {
        orderRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();

        // Seed two test stocks
        stockService.createStockIfAbsent("RELIANCE", "Reliance Industries Ltd", new BigDecimal("2950.00"));
        stockService.createStockIfAbsent("TCS", "Tata Consultancy Services Ltd", new BigDecimal("3850.00"));
    }

    @Test
    void getAllStocks_ReturnsListWithSimulatedLabel() throws Exception {
        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("RELIANCE"))
                .andExpect(jsonPath("$[0].simulated").value(true))
                .andExpect(jsonPath("$[0].currency").value("INR"))
                .andExpect(jsonPath("$[0].currentPrice").value(2950.00))
                .andExpect(jsonPath("$[1].symbol").value("TCS"));
    }

    @Test
    void getStockBySymbol_Found_ReturnsStock() throws Exception {
        mockMvc.perform(get("/api/stocks/RELIANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("RELIANCE"))
                .andExpect(jsonPath("$.companyName").value("Reliance Industries Ltd"))
                .andExpect(jsonPath("$.currentPrice").value(2950.00))
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    void getStockBySymbol_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/stocks/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("UNKNOWN")));
    }

    @Test
    void searchStocks_MatchesQuery() throws Exception {
        mockMvc.perform(get("/api/stocks/search").param("query", "tata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("TCS"));
    }

    @Test
    void getStockHistory_ReturnsChronologicalPrices() throws Exception {
        // Add a price update
        stockService.updateStockPrice("RELIANCE", new BigDecimal("2980.00"), Instant.now());

        mockMvc.perform(get("/api/stocks/RELIANCE/history").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].price").value(2950.00))
                .andExpect(jsonPath("$[0].simulated").value(true))
                .andExpect(jsonPath("$[1].price").value(2980.00));
    }

    @Test
    void security_StocksPublic_UserMeStillProtected() throws Exception {
        // /api/stocks is accessible without authentication
        mockMvc.perform(get("/api/stocks"))
                .andExpect(status().isOk());

        // /api/user/me remains strictly protected
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }
}
