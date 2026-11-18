package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.exception.InsufficientSharesException;
import com.stocktrading.simulator.model.Holding;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("JpaHoldingsAdapter Integration Tests")
class JpaHoldingsAdapterTest {

    @Autowired
    private JpaHoldingsAdapter jpaHoldingsAdapter;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private User testUser;
    private Stock testStock;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanup();

        testUser = userRepository.save(new User("Holdings User", "holdingsuser", "holdings@example.com", "hash"));
        testStock = stockRepository.save(new Stock("TCS", "Tata Consultancy Services Ltd", new BigDecimal("3500.00")));
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
    @DisplayName("addShares: Creates new holding with correct quantity and WAC within transaction")
    void addShares_ValidBuy_CreatesHolding() {
        transactionTemplate.execute(status -> {
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("3500.00"), 101L);
            return null;
        });

        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(10, holding.getQuantity());
        assertEquals(0, new BigDecimal("3500.000000").compareTo(holding.getAverageCost()));
        assertEquals(0, BigDecimal.ZERO.compareTo(holding.getTotalRealizedPnl()));
    }

    @Test
    @DisplayName("addShares: Multiple buys update weighted-average cost correctly")
    void addShares_MultipleBuys_UpdatesWac() {
        transactionTemplate.execute(status -> {
            // First buy: 10 @ 3000.00 (cost: 30,000)
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("3000.00"), 101L);
            // Second buy: 10 @ 4000.00 (cost: 40,000, total: 70,000 / 20 = 3500.00)
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("4000.00"), 102L);
            return null;
        });

        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(20, holding.getQuantity());
        assertEquals(0, new BigDecimal("3500.000000").compareTo(holding.getAverageCost()));
    }

    @Test
    @DisplayName("deductAvailableShares: Valid sale decreases shares and accumulates realized P&L")
    void deductAvailableShares_ValidSell_DecreasesSharesAndCalculatesRealizedPnl() {
        transactionTemplate.execute(status -> {
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("3000.00"), 101L);
            return null;
        });

        transactionTemplate.execute(status -> {
            // Sell 4 @ 3500.00 -> gain = 4 * (3500 - 3000) = +2000.00
            jpaHoldingsAdapter.deductAvailableShares(testUser.getId(), "TCS", 4, new BigDecimal("3500.00"), 102L);
            return null;
        });

        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(6, holding.getQuantity());
        assertEquals(0, new BigDecimal("3000.000000").compareTo(holding.getAverageCost()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(holding.getTotalRealizedPnl()));
    }

    @Test
    @DisplayName("deductAvailableShares: Insufficient shares throws InsufficientSharesException")
    void deductAvailableShares_InsufficientShares_ThrowsException() {
        transactionTemplate.execute(status -> {
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 5, new BigDecimal("3000.00"), 101L);
            return null;
        });

        assertThrows(InsufficientSharesException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.deductAvailableShares(testUser.getId(), "TCS", 10, new BigDecimal("3500.00"), 102L);
                return null;
            })
        );

        // Position remains untouched
        Holding holding = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(5, holding.getQuantity());
    }

    @Test
    @DisplayName("deductAvailableShares: No holding throws InsufficientSharesException")
    void deductAvailableShares_NoHolding_ThrowsException() {
        assertThrows(InsufficientSharesException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.deductAvailableShares(testUser.getId(), "TCS", 5, new BigDecimal("3500.00"), 101L);
                return null;
            })
        );
    }

    @Test
    @DisplayName("addShares: Invocation outside active transaction throws IllegalTransactionStateException (MANDATORY)")
    void addShares_WithoutActiveTransaction_ThrowsException() {
        assertThrows(IllegalTransactionStateException.class, () ->
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("3500.00"), 101L)
        );
    }

    @Test
    @DisplayName("deductAvailableShares: Invocation outside active transaction throws IllegalTransactionStateException (MANDATORY)")
    void deductAvailableShares_WithoutActiveTransaction_ThrowsException() {
        assertThrows(IllegalTransactionStateException.class, () ->
            jpaHoldingsAdapter.deductAvailableShares(testUser.getId(), "TCS", 5, new BigDecimal("3500.00"), 101L)
        );
    }

    @Test
    @DisplayName("optimisticLocking: Concurrent modification on holding detected via @Version")
    void optimisticLocking_DetectsConcurrentModification() {
        transactionTemplate.execute(status -> {
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("3500.00"), 101L);
            return null;
        });

        Holding h1 = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        Holding h2 = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();

        // Transaction A updates holding
        transactionTemplate.execute(status -> {
            h1.recordBuy(5, new BigDecimal("3600.00"));
            holdingRepository.saveAndFlush(h1);
            return null;
        });

        // Transaction B attempts to save stale holding instance
        assertThrows(ObjectOptimisticLockingFailureException.class, () ->
            transactionTemplate.execute(status -> {
                h2.recordBuy(5, new BigDecimal("3700.00"));
                holdingRepository.saveAndFlush(h2);
                return null;
            })
        );
    }

    @Test
    @DisplayName("Parameter validation: Rejects invalid quantities and prices")
    void parameterValidation_RejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.addShares(null, "TCS", 10, new BigDecimal("3500.00"), 1L);
                return null;
            })
        );
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.addShares(testUser.getId(), "", 10, new BigDecimal("3500.00"), 1L);
                return null;
            })
        );
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 0, new BigDecimal("3500.00"), 1L);
                return null;
            })
        );
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("0.00"), 1L);
                return null;
            })
        );
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("3500.001"), 1L);
                return null;
            })
        );
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 10, new BigDecimal("3500.00"), null);
                return null;
            })
        );
    }

    @Test
    @DisplayName("Aligned DECIMAL(12,2) price limit (9,999,999,999.99) is accepted; above limit is rejected")
    void priceBoundaryAlignment_AcceptsMaxDecimal12_2AndRejectsAbove() {
        BigDecimal maxDecimal12_2 = new BigDecimal("9999999999.99");
        BigDecimal aboveLimit = new BigDecimal("10000000000.00");

        transactionTemplate.execute(status -> {
            jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 1, maxDecimal12_2, 100L);
            return null;
        });

        Holding h = holdingRepository.findByUserIdAndStockId(testUser.getId(), testStock.getId()).orElseThrow();
        assertEquals(1, h.getQuantity());
        assertEquals(0, new BigDecimal("9999999999.990000").compareTo(h.getAverageCost()));

        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.addShares(testUser.getId(), "TCS", 1, aboveLimit, 101L);
                return null;
            })
        );
        assertThrows(IllegalArgumentException.class, () ->
            transactionTemplate.execute(status -> {
                jpaHoldingsAdapter.deductAvailableShares(testUser.getId(), "TCS", 1, aboveLimit, 102L);
                return null;
            })
        );
    }
}
