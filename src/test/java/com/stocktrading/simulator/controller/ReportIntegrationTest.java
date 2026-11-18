package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.model.PaymentTransaction;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.*;
import com.stocktrading.simulator.service.OrderExecutionService;
import com.stocktrading.simulator.service.OrderService;
import com.stocktrading.simulator.service.StockService;
import com.stocktrading.simulator.service.UserService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Reports CSV/PDF Integration Tests (Real Trades, Holdings & Top-Ups)")
class ReportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private StockService stockService;
    @Autowired private OrderService orderService;
    @Autowired private OrderExecutionService orderExecutionService;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private AuthTokenRepository authTokenRepository;
    @Autowired private HoldingRepository holdingRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private StockPriceHistoryRepository historyRepository;
    @Autowired private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        cleanup();
        stockService.createStockIfAbsent("INFY", "Infosys Ltd", new BigDecimal("1500.00"));
        stockService.createStockIfAbsent("TCS", "Tata Consultancy Services", new BigDecimal("3500.00"));
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
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User registerUser(String name, String username, String email) {
        RegisterRequest req = new RegisterRequest();
        req.setName(name);
        req.setUsername(username);
        req.setEmail(email);
        req.setPassword("password123");
        req.setConfirmPassword("password123");
        userService.registerUser(req);
        return userRepository.findByUsername(username).orElseThrow();
    }

    private String csv(String path, String username) throws Exception {
        MvcResult r = mockMvc.perform(get(path).param("format", "csv").with(user(username)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
            .andReturn();
        return new String(r.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private String pdfText(String path, String username) throws Exception {
        MvcResult r = mockMvc.perform(get(path).param("format", "pdf").with(user(username)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
            .andReturn();
        byte[] bytes = r.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("Trading history and portfolio CSV/PDF exports contain REAL executed trades and holdings scoped to caller")
    void tradingHistoryAndPortfolioReportsContainRealUserRecordsOnly() throws Exception {
        registerUser("Alice Liddell", "alice", "alice@example.com");
        registerUser("Bob Builder", "bob", "bob@example.com");

        // Alice buys 10 INFY @ 1500.00
        OrderResponse aliceBuy = orderService.createBuyOrder("alice", new OrderRequest(UUID.randomUUID().toString(), "INFY", 10));
        orderExecutionService.executeOrder(aliceBuy.getId(), "alice");

        // Bob buys 5 TCS @ 3500.00
        OrderResponse bobBuy = orderService.createBuyOrder("bob", new OrderRequest(UUID.randomUUID().toString(), "TCS", 5));
        orderExecutionService.executeOrder(bobBuy.getId(), "bob");

        // Update INFY price to 1650.00 -> Alice has +1500.00 unrealized P&L (16500 value vs 15000 cost)
        stockService.updateStockPrice("INFY", new BigDecimal("1650.00"));

        // Alice trading history CSV & PDF: contains INFY BUY 10 @ 1500.00, NEVER Bob's TCS trade
        String tradesCsv = csv("/api/reports/trading-history", "alice");
        assertThat(tradesCsv).startsWith("\uFEFF");
        assertThat(tradesCsv).contains("INFY,BUY,10,1500.00,15000.00");
        assertThat(tradesCsv).doesNotContain("TCS");

        String tradesPdf = pdfText("/api/reports/trading-history", "alice");
        assertThat(tradesPdf).contains("Trading history", "INFY", "BUY", "1500.00", "15000.00");
        assertThat(tradesPdf).doesNotContain("TCS");

        // Alice portfolio CSV & PDF: contains INFY qty=10, avg=1500.00, cur=1650.00, val=16500.00, pnl=1500.00; NEVER Bob's TCS
        String portfolioCsv = csv("/api/reports/portfolio", "alice");
        assertThat(portfolioCsv).contains("INFY,10,1500.00,1650.00,16500.00,1500.00");
        assertThat(portfolioCsv).doesNotContain("TCS");

        String portfolioPdf = pdfText("/api/reports/portfolio", "alice");
        assertThat(portfolioPdf).contains("Portfolio summary", "INFY", "16500.00", "1500.00");
        assertThat(portfolioPdf).doesNotContain("TCS");
    }

    @Test
    @DisplayName("Profile and transactions reports escape formulas, preserve Unicode, omit secrets, and isolate users")
    void profileAndTransactionsReportsSecurityAndIsolation() throws Exception {
        User alice = registerUser("=SUM(A1:A9), \"Zoë\"", "alice", "alice@example.com");
        User bob = registerUser("Bob Secret", "bob", "bob@example.com");

        paymentTransactionRepository.save(new PaymentTransaction(alice, "alice-rep-0001", new BigDecimal("2500.00")));
        paymentTransactionRepository.save(new PaymentTransaction(bob, "bob-rep-000001", new BigDecimal("9999.00")));

        String profileCsv = csv("/api/reports/profile", "alice");
        assertThat(profileCsv).contains("\"'=SUM(A1:A9), \"\"Zoë\"\"\"");
        assertThat(profileCsv).contains(String.valueOf(alice.getPublicUserId()), "alice@example.com");
        assertThat(profileCsv).doesNotContain("bob@example.com", alice.getPasswordHash());

        String profilePdf = pdfText("/api/reports/profile", "alice");
        assertThat(profilePdf).contains("Profile summary", "alice@example.com", "Page 1 of 1");

        String txnsCsv = csv("/api/reports/transactions", "alice");
        assertThat(txnsCsv).contains("2500.00").doesNotContain("9999.00");
    }

    @Test
    @DisplayName("All report endpoints enforce authentication and reject unsupported format parameters")
    void reportEndpointsRequireAuthenticationAndValidFormat() throws Exception {
        mockMvc.perform(get("/api/reports/profile").param("format", "csv")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/transactions").param("format", "pdf")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/trading-history").param("format", "csv")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/portfolio").param("format", "pdf")).andExpect(status().isUnauthorized());

        registerUser("Alice", "alice", "alice@example.com");
        mockMvc.perform(get("/api/reports/profile").param("format", "xlsx").with(user("alice")))
            .andExpect(status().isBadRequest());
    }
}
