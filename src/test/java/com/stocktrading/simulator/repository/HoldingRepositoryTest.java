package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Holding;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("HoldingRepository Integration Tests")
class HoldingRepositoryTest {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;
    private Stock stockA;
    private Stock stockB;

    @BeforeEach
    void setUp() {
        cleanup();

        user1 = userRepository.save(new User("User One", "user1", "user1@example.com", "hash1"));
        user2 = userRepository.save(new User("User Two", "user2", "user2@example.com", "hash2"));

        stockA = stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("150.00")));
        stockB = stockRepository.save(new Stock("TSLA", "Tesla Inc.", new BigDecimal("250.00")));
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
    @DisplayName("findByUserIdAndStockId: Finds holding for specific user and stock")
    void findByUserIdAndStockId_Success() {
        Holding holding = new Holding(user1, stockA);
        holding.recordBuy(10, new BigDecimal("150.00"));
        holding = holdingRepository.save(holding);

        Optional<Holding> found = holdingRepository.findByUserIdAndStockId(user1.getId(), stockA.getId());

        assertTrue(found.isPresent());
        assertEquals(holding.getId(), found.get().getId());
        assertEquals(10, found.get().getQuantity());
        assertEquals(0, new BigDecimal("150.000000").compareTo(found.get().getAverageCost()));
    }

    @Test
    @DisplayName("findByUserId: Retrieves all holdings belonging to user")
    void findByUserId_RetrievesAllHoldingsForUser() {
        Holding h1 = new Holding(user1, stockA);
        h1.recordBuy(10, new BigDecimal("150.00"));
        holdingRepository.save(h1);

        Holding h2 = new Holding(user1, stockB);
        h2.recordBuy(5, new BigDecimal("250.00"));
        holdingRepository.save(h2);

        List<Holding> user1Holdings = holdingRepository.findByUserId(user1.getId());
        assertEquals(2, user1Holdings.size());
    }

    @Test
    @DisplayName("findByUserIdAndQuantityGreaterThan: Filters out closed (zero-quantity) positions")
    void findByUserIdAndQuantityGreaterThan_FiltersZeroQuantityPositions() {
        Holding activeHolding = new Holding(user1, stockA);
        activeHolding.recordBuy(15, new BigDecimal("150.00"));
        holdingRepository.save(activeHolding);

        Holding closedHolding = new Holding(user1, stockB);
        closedHolding.recordBuy(10, new BigDecimal("250.00"));
        closedHolding.recordSell(10, new BigDecimal("260.00")); // closed: quantity = 0
        holdingRepository.save(closedHolding);

        List<Holding> positiveHoldings = holdingRepository.findByUserIdAndQuantityGreaterThan(user1.getId(), 0);

        assertEquals(1, positiveHoldings.size());
        assertEquals(stockA.getId(), positiveHoldings.get(0).getStock().getId());
        assertEquals(15, positiveHoldings.get(0).getQuantity());
    }

    @Test
    @DisplayName("User isolation: One user's holdings are not returned for another user")
    void userIsolation_NoCrossUserLeakage() {
        Holding h1 = new Holding(user1, stockA);
        h1.recordBuy(20, new BigDecimal("150.00"));
        holdingRepository.save(h1);

        Holding h2 = new Holding(user2, stockB);
        h2.recordBuy(30, new BigDecimal("250.00"));
        holdingRepository.save(h2);

        List<Holding> user1Holdings = holdingRepository.findByUserId(user1.getId());
        List<Holding> user2Holdings = holdingRepository.findByUserId(user2.getId());

        assertEquals(1, user1Holdings.size());
        assertEquals(user1.getId(), user1Holdings.get(0).getUser().getId());

        assertEquals(1, user2Holdings.size());
        assertEquals(user2.getId(), user2Holdings.get(0).getUser().getId());

        Optional<Holding> crossLookup = holdingRepository.findByUserIdAndStockId(user1.getId(), stockB.getId());
        assertFalse(crossLookup.isPresent());
    }
}
