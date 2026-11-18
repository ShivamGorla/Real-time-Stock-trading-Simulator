package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.model.Holding;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("PortfolioController Integration Tests")
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private WalletService walletService;

    private User portUser;
    private User otherUser;
    private Stock infyStock;
    private Stock relianceStock;

    @BeforeEach
    void setUp() {
        cleanup();

        portUser = userRepository.save(new User("Portfolio User", "portuser", "portuser@example.com", "hash"));
        walletService.createWalletWithInitialBalance(portUser); // 100,000 INR

        otherUser = userRepository.save(new User("Other User", "otheruser", "otheruser@example.com", "hashOther"));
        walletService.createWalletWithInitialBalance(otherUser);

        infyStock = stockRepository.save(new Stock("INFY", "Infosys Limited", new BigDecimal("1550.00")));
        relianceStock = stockRepository.save(new Stock("RELIANCE", "Reliance Industries Limited", new BigDecimal("2950.00")));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Authenticated request: Returns HTTP 200 with accurate portfolio structure")
    @WithMockUser(username = "portuser")
    void getPortfolio_Authenticated_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/portfolio")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("portuser"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.walletBalance").value(100000.0))
                .andExpect(jsonPath("$.totalInvestedValue").value(0.0))
                .andExpect(jsonPath("$.totalCurrentMarketValue").value(0.0))
                .andExpect(jsonPath("$.totalUnrealizedPnl").value(0.0))
                .andExpect(jsonPath("$.totalRealizedPnl").value(0.0))
                .andExpect(jsonPath("$.totalPnl").value(0.0))
                .andExpect(jsonPath("$.unrealizedReturnPercentage").value(0.0))
                .andExpect(jsonPath("$.totalAccountEquity").value(100000.0))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.holdings", hasSize(0)));
    }

    @Test
    @DisplayName("Unauthenticated request: Returns HTTP 401 Unauthorized")
    void getPortfolio_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/portfolio")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Active portfolio: Default includeClosed=false returns active positions with correct calculations")
    @WithMockUser(username = "portuser")
    void getPortfolio_ActivePositions_DefaultExcludeClosed() throws Exception {
        // Set up holding: 10 INFY @ 1500.00 (invested = 15,000, current market = 15,500)
        Holding holding = new Holding(portUser, infyStock);
        holding.recordBuy(10, new BigDecimal("1500.00"));
        holdingRepository.save(holding);

        mockMvc.perform(get("/api/portfolio")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("portuser"))
                .andExpect(jsonPath("$.totalInvestedValue").value(15000.0))
                .andExpect(jsonPath("$.totalCurrentMarketValue").value(15500.0))
                .andExpect(jsonPath("$.totalUnrealizedPnl").value(500.0))
                .andExpect(jsonPath("$.totalRealizedPnl").value(0.0))
                .andExpect(jsonPath("$.totalPnl").value(500.0))
                .andExpect(jsonPath("$.unrealizedReturnPercentage").value(3.33))
                .andExpect(jsonPath("$.totalAccountEquity").value(115500.0))
                .andExpect(jsonPath("$.holdings", hasSize(1)))
                .andExpect(jsonPath("$.holdings[0].symbol").value("INFY"))
                .andExpect(jsonPath("$.holdings[0].quantity").value(10))
                .andExpect(jsonPath("$.holdings[0].averageCost").value(1500.0))
                .andExpect(jsonPath("$.holdings[0].currentPrice").value(1550.0))
                .andExpect(jsonPath("$.holdings[0].investedValue").value(15000.0))
                .andExpect(jsonPath("$.holdings[0].currentMarketValue").value(15500.0))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnl").value(500.0));
    }

    @Test
    @DisplayName("Closed positions: includeClosed=true includes quantity=0 holding, false excludes it")
    @WithMockUser(username = "portuser")
    void getPortfolio_ClosedPositions_FilteredCorrectly() throws Exception {
        // Active holding: INFY
        Holding activeHolding = new Holding(portUser, infyStock);
        activeHolding.recordBuy(10, new BigDecimal("1550.00"));
        holdingRepository.save(activeHolding);

        // Closed holding: RELIANCE (bought 5 @ 2900, sold 5 @ 3000 -> realized PnL = +500)
        Holding closedHolding = new Holding(portUser, relianceStock);
        closedHolding.recordBuy(5, new BigDecimal("2900.00"));
        closedHolding.recordSell(5, new BigDecimal("3000.00"));
        holdingRepository.save(closedHolding);

        // Default (exclude closed)
        mockMvc.perform(get("/api/portfolio")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRealizedPnl").value(500.0))
                .andExpect(jsonPath("$.holdings", hasSize(1)))
                .andExpect(jsonPath("$.holdings[0].symbol").value("INFY"));

        // includeClosed=true
        mockMvc.perform(get("/api/portfolio?includeClosed=true")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRealizedPnl").value(500.0))
                .andExpect(jsonPath("$.holdings", hasSize(2)));
    }

    @Test
    @DisplayName("User isolation: portuser does not see otherUser holdings")
    @WithMockUser(username = "portuser")
    void getPortfolio_UserIsolation_DoesNotLeakOtherUsersHoldings() throws Exception {
        // otherUser has holding
        Holding otherHolding = new Holding(otherUser, infyStock);
        otherHolding.recordBuy(50, new BigDecimal("1550.00"));
        holdingRepository.save(otherHolding);

        mockMvc.perform(get("/api/portfolio")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("portuser"))
                .andExpect(jsonPath("$.totalInvestedValue").value(0.0))
                .andExpect(jsonPath("$.holdings", hasSize(0)));
    }
}
