package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.TradeRepository;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.repository.WalletRepository;
import com.stocktrading.simulator.repository.WalletTransactionRepository;
import com.stocktrading.simulator.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private WalletService walletService;

    @BeforeEach
    @AfterEach
    void setup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getWallet_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/wallet"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "walletuser")
    void getWallet_Authenticated_ReturnsCorrectBalanceAndPayload() throws Exception {
        User user = new User("Wallet User", "walletuser", "walletuser@example.com", "hash123");
        user = userRepository.save(user);
        Wallet wallet = walletRepository.save(new Wallet(user, new BigDecimal("100000.00"), "INR"));

        mockMvc.perform(get("/api/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(wallet.getId()))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.username").value("walletuser"))
                .andExpect(jsonPath("$.balance").value(100000.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getTransactions_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/wallet/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "historyuser")
    void getTransactions_Authenticated_ReturnsPaginatedHistory() throws Exception {
        User user = new User("History User", "historyuser", "history@example.com", "hash123");
        user = userRepository.save(user);
        Wallet wallet = walletService.createWalletWithInitialBalance(user);

        walletService.executeCredit(
            user.getId(), new BigDecimal("5000.00"), TransactionCategory.TOP_UP, "TOPUP-H1", "Top up"
        );
        walletService.executeDebit(
            user.getId(), new BigDecimal("2000.00"), TransactionCategory.TRADE_BUY, "ORDER-H1", "Trade buy"
        );

        mockMvc.perform(get("/api/wallet/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3))
                // Ordered by createdAt DESC, id DESC: ORDER-H1 is most recent
                .andExpect(jsonPath("$.content[0].referenceId").value("ORDER-H1"))
                .andExpect(jsonPath("$.content[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].amount").value(2000.00))
                .andExpect(jsonPath("$.content[1].referenceId").value("TOPUP-H1"))
                .andExpect(jsonPath("$.content[1].type").value("CREDIT"))
                .andExpect(jsonPath("$.content[2].referenceId").value("INIT-" + user.getId()));
    }

    @Test
    @WithMockUser(username = "userA")
    void getTransactions_UserIsolation_DoesNotReturnOtherUserTransactions() throws Exception {
        // Create User A
        User userA = new User("User A", "userA", "usera@example.com", "hash123");
        userA = userRepository.save(userA);
        walletService.createWalletWithInitialBalance(userA);

        // Create User B
        User userB = new User("User B", "userB", "userb@example.com", "hash123");
        userB = userRepository.save(userB);
        walletService.createWalletWithInitialBalance(userB);
        walletService.executeCredit(
            userB.getId(), new BigDecimal("50000.00"), TransactionCategory.TOP_UP, "TOPUP-B", "Secret User B top up"
        );

        // Authenticated as User A -> must only see User A's initial transaction
        mockMvc.perform(get("/api/wallet/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].referenceId").value("INIT-" + userA.getId()));
    }

    @Test
    @WithMockUser(username = "filteruser")
    void getTransactions_FilterByTypeAndCategory_ReturnsMatchingSubset() throws Exception {
        User user = new User("Filter User", "filteruser", "filter@example.com", "hash123");
        user = userRepository.save(user);
        walletService.createWalletWithInitialBalance(user);

        walletService.executeDebit(
            user.getId(), new BigDecimal("1000.00"), TransactionCategory.TRADE_BUY, "BUY-1", "Buy shares"
        );
        walletService.executeCredit(
            user.getId(), new BigDecimal("2000.00"), TransactionCategory.TRADE_SELL, "SELL-1", "Sell shares"
        );

        // Filter by type=DEBIT
        mockMvc.perform(get("/api/wallet/transactions")
                .param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].referenceId").value("BUY-1"));

        // Filter by category=TRADE_SELL
        mockMvc.perform(get("/api/wallet/transactions")
                .param("category", "TRADE_SELL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].referenceId").value("SELL-1"));
    }

    @Test
    @WithMockUser(username = "pageuser")
    void getTransactions_Pagination_RespectsPageAndSize() throws Exception {
        User user = new User("Page User", "pageuser", "page@example.com", "hash123");
        user = userRepository.save(user);
        walletService.createWalletWithInitialBalance(user);

        for (int i = 1; i <= 5; i++) {
            walletService.executeCredit(
                user.getId(), new BigDecimal("100.00"), TransactionCategory.TOP_UP, "TOP-" + i, "Top up " + i
            );
        }

        // Total elements = 1 (init) + 5 = 6. Request page 0 with size 2
        mockMvc.perform(get("/api/wallet/transactions")
                .param("page", "0")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    @WithMockUser(username = "validdateuser")
    void getTransactions_ValidDateRange_ReturnsOk() throws Exception {
        User user = new User("Valid Date User", "validdateuser", "validdate@example.com", "hash123");
        user = userRepository.save(user);
        walletService.createWalletWithInitialBalance(user);

        mockMvc.perform(get("/api/wallet/transactions")
                .param("startDate", "2026-11-01")
                .param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "equaldateuser")
    void getTransactions_EqualDates_ReturnsBadRequest() throws Exception {
        User user = new User("Equal Date User", "equaldateuser", "equaldate@example.com", "hash123");
        user = userRepository.save(user);
        walletService.createWalletWithInitialBalance(user);

        mockMvc.perform(get("/api/wallet/transactions")
                .param("startDate", "2026-11-17")
                .param("endDate", "2026-11-17"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("startDate must be strictly before endDate"));
    }

    @Test
    @WithMockUser(username = "inverteddateuser")
    void getTransactions_InvertedDateRange_ReturnsBadRequest() throws Exception {
        User user = new User("Inverted Date User", "inverteddateuser", "inverteddate@example.com", "hash123");
        user = userRepository.save(user);
        walletService.createWalletWithInitialBalance(user);

        mockMvc.perform(get("/api/wallet/transactions")
                .param("startDate", "2026-11-20")
                .param("endDate", "2026-11-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("startDate must be strictly before endDate"));
    }
}
