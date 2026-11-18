package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.StockPriceHistoryResponse;
import com.stocktrading.simulator.dto.StockResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.StockPriceHistoryRepository;
import com.stocktrading.simulator.repository.StockRepository;
import com.stocktrading.simulator.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockPriceSimulator stockPriceSimulator;

    @BeforeEach
    void setup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    void createStockIfAbsent_NewStock_Success() {
        Stock stock = stockService.createStockIfAbsent("TEST1", "Test Company 1", new BigDecimal("100.50"));

        assertNotNull(stock.getId());
        assertEquals("TEST1", stock.getSymbol());
        assertEquals(new BigDecimal("100.50"), stock.getCurrentPrice());
        assertEquals(new BigDecimal("100.50"), stock.getPreviousPrice());
        assertEquals(1, historyRepository.countByStock(stock));
    }

    @Test
    void createStockIfAbsent_ExistingStock_IdempotentAndDoesNotOverwritePrice() {
        Stock first = stockService.createStockIfAbsent("TEST2", "Test Company 2", new BigDecimal("200.00"));
        stockService.updateStockPrice("TEST2", new BigDecimal("250.00"), Instant.now());

        // Call again with different initial price — should NOT overwrite the updated 250.00
        Stock second = stockService.createStockIfAbsent("TEST2", "Test Company 2", new BigDecimal("200.00"));

        assertEquals(first.getId(), second.getId());
        assertEquals(new BigDecimal("250.00"), second.getCurrentPrice());
    }

    @Test
    void updateStockPrice_UpdatesPreviousAndCurrentAndAppendsHistory() {
        stockService.createStockIfAbsent("TEST3", "Test Company 3", new BigDecimal("300.00"));

        Stock updated = stockService.updateStockPrice("TEST3", new BigDecimal("330.00"), Instant.now());

        assertEquals(new BigDecimal("330.00"), updated.getCurrentPrice());
        assertEquals(new BigDecimal("300.00"), updated.getPreviousPrice());

        List<StockPriceHistoryResponse> history = stockService.getStockHistory("TEST3", 10);
        assertEquals(2, history.size());
        // Verify chronological order: first entry is initial, second is updated
        assertEquals(new BigDecimal("300.00"), history.get(0).getPrice());
        assertEquals(new BigDecimal("330.00"), history.get(1).getPrice());
        assertTrue(history.get(0).isSimulated());
    }

    @Test
    void updateStockPrice_NegativeOrZeroPrice_ThrowsException() {
        stockService.createStockIfAbsent("TEST4", "Test Company 4", new BigDecimal("400.00"));

        assertThrows(IllegalArgumentException.class, () ->
                stockService.updateStockPrice("TEST4", new BigDecimal("-10.00"), Instant.now()));

        assertThrows(IllegalArgumentException.class, () ->
                stockService.updateStockPrice("TEST4", BigDecimal.ZERO, Instant.now()));
    }

    @Test
    void getStockBySymbol_UnknownSymbol_ThrowsResourceNotFoundException() {
        assertThrows(ResourceNotFoundException.class, () ->
                stockService.getStockBySymbol("DOESNOTEXIST"));
    }

    @Test
    void searchStocks_BySymbolOrCompanyName() {
        stockService.createStockIfAbsent("INFY", "Infosys Ltd", new BigDecimal("1500.00"));
        stockService.createStockIfAbsent("TCS", "Tata Consultancy Services Ltd", new BigDecimal("3500.00"));

        List<StockResponse> searchSymbol = stockService.searchStocks("inf");
        assertEquals(1, searchSymbol.size());
        assertEquals("INFY", searchSymbol.get(0).getSymbol());

        List<StockResponse> searchCompany = stockService.searchStocks("consultancy");
        assertEquals(1, searchCompany.size());
        assertEquals("TCS", searchCompany.get(0).getSymbol());
    }

    @Test
    void getStockHistory_BoundedLimitAndChronological() {
        stockService.createStockIfAbsent("TEST5", "Test Company 5", new BigDecimal("100.00"));

        // Add 5 updates
        for (int i = 1; i <= 5; i++) {
            stockService.updateStockPrice("TEST5", BigDecimal.valueOf(100 + i * 10), Instant.now());
        }

        // Limit to 3 latest entries
        List<StockPriceHistoryResponse> bounded = stockService.getStockHistory("TEST5", 3);
        assertEquals(3, bounded.size());

        // Chronological: prices should be increasing (130, 140, 150)
        assertEquals(new BigDecimal("130.00"), bounded.get(0).getPrice());
        assertEquals(new BigDecimal("140.00"), bounded.get(1).getPrice());
        assertEquals(new BigDecimal("150.00"), bounded.get(2).getPrice());
    }

    @Test
    void getStockHistory_IdenticalTimestamps_DeterministicSecondarySortByIdAndChronologicalOrder() {
        stockService.createStockIfAbsent("TIE1", "Tie Break Co", new BigDecimal("100.00"));
        Instant identicalTimestamp = Instant.now().plusSeconds(60);

        // Record 5 distinct prices at the exact same timestamp
        stockService.updateStockPrice("TIE1", new BigDecimal("110.00"), identicalTimestamp);
        stockService.updateStockPrice("TIE1", new BigDecimal("120.00"), identicalTimestamp);
        stockService.updateStockPrice("TIE1", new BigDecimal("130.00"), identicalTimestamp);
        stockService.updateStockPrice("TIE1", new BigDecimal("140.00"), identicalTimestamp);
        stockService.updateStockPrice("TIE1", new BigDecimal("150.00"), identicalTimestamp);

        // Full history must preserve exact chronological insertion order
        List<StockPriceHistoryResponse> full = stockService.getStockHistory("TIE1", 10);
        assertEquals(6, full.size());
        assertEquals(new BigDecimal("100.00"), full.get(0).getPrice());
        assertEquals(new BigDecimal("110.00"), full.get(1).getPrice());
        assertEquals(new BigDecimal("120.00"), full.get(2).getPrice());
        assertEquals(new BigDecimal("130.00"), full.get(3).getPrice());
        assertEquals(new BigDecimal("140.00"), full.get(4).getPrice());
        assertEquals(new BigDecimal("150.00"), full.get(5).getPrice());

        // Bounded limit=3 must return the 3 latest rows (highest IDs) in chronological order
        List<StockPriceHistoryResponse> latestThree = stockService.getStockHistory("TIE1", 3);
        assertEquals(3, latestThree.size());
        assertEquals(new BigDecimal("130.00"), latestThree.get(0).getPrice());
        assertEquals(new BigDecimal("140.00"), latestThree.get(1).getPrice());
        assertEquals(new BigDecimal("150.00"), latestThree.get(2).getPrice());
    }

    @Test
    void stockPriceSimulator_CalculateNextPrice_NeverBelowFloor() {
        BigDecimal verySmallPrice = new BigDecimal("0.50");
        BigDecimal nextPrice = stockPriceSimulator.calculateNextPrice(verySmallPrice, 0.50);

        assertTrue(nextPrice.compareTo(new BigDecimal("1.00")) >= 0,
                "Simulated price must stay positive and at or above floor");
    }

    @Test
    void stockPriceSimulator_CalculateNextPrice_HandlesInvalidAndNegativeChangeFactors() {
        BigDecimal initialPrice = new BigDecimal("100.00");

        // Zero change factor
        BigDecimal nextZero = stockPriceSimulator.calculateNextPrice(initialPrice, 0.0);
        assertEquals(new BigDecimal("100.00"), nextZero);

        // Negative change factor
        BigDecimal nextNegative = stockPriceSimulator.calculateNextPrice(initialPrice, -0.05);
        assertEquals(new BigDecimal("100.00"), nextNegative);

        // NaN change factor
        BigDecimal nextNaN = stockPriceSimulator.calculateNextPrice(initialPrice, Double.NaN);
        assertEquals(new BigDecimal("100.00"), nextNaN);

        // Infinite change factor
        BigDecimal nextInf = stockPriceSimulator.calculateNextPrice(initialPrice, Double.POSITIVE_INFINITY);
        assertEquals(new BigDecimal("100.00"), nextInf);
    }

    @Test
    void stockPriceSimulator_CalculateNextPrice_NeverExceedsCeiling() {
        BigDecimal nearCeiling = new BigDecimal("9999999999.00");
        BigDecimal nextPrice = stockPriceSimulator.calculateNextPrice(nearCeiling, 0.50);

        assertTrue(nextPrice.compareTo(new BigDecimal("9999999999.99")) <= 0,
                "Simulated price must not exceed maximum decimal precision ceiling");
    }

    @Test
    void stockPriceSimulator_CalculateNextPrice_OverCeilingAndInvalidChangeFactor_ClampsToCeiling() {
        BigDecimal overCeiling = new BigDecimal("12000000000.00");

        assertEquals(new BigDecimal("9999999999.99"),
                stockPriceSimulator.calculateNextPrice(overCeiling, 0.0));
        assertEquals(new BigDecimal("9999999999.99"),
                stockPriceSimulator.calculateNextPrice(overCeiling, -0.02));
        assertEquals(new BigDecimal("9999999999.99"),
                stockPriceSimulator.calculateNextPrice(overCeiling, Double.NaN));
        assertEquals(new BigDecimal("9999999999.99"),
                stockPriceSimulator.calculateNextPrice(overCeiling, Double.POSITIVE_INFINITY));
    }

    @Test
    void stockPriceSimulator_CalculateNextPrice_SubFloorAndInvalidChangeFactor_ClampsToFloor() {
        BigDecimal subFloor = new BigDecimal("0.50");

        assertEquals(new BigDecimal("1.00"),
                stockPriceSimulator.calculateNextPrice(subFloor, 0.0));
        assertEquals(new BigDecimal("1.00"),
                stockPriceSimulator.calculateNextPrice(subFloor, -0.05));
    }

    @Test
    void stockPriceSimulator_CalculateNextPrice_MultiDecimalAndInvalidChangeFactor_RoundsToTwoDecimals() {
        BigDecimal multiDecimal = new BigDecimal("100.126");

        assertEquals(new BigDecimal("100.13"),
                stockPriceSimulator.calculateNextPrice(multiDecimal, 0.0));
    }

    @Test
    void createStockIfAbsent_RoundingAndBoundaryValidation() {
        // 0.004 rounds to 0.00 -> rejected before save
        assertThrows(IllegalArgumentException.class, () ->
                stockService.createStockIfAbsent("VAL1", "Validation 1", new BigDecimal("0.004")));
        assertEquals(0, stockRepository.count());
        assertEquals(0, historyRepository.count());

        // 0.005 rounds to 0.01 -> accepted
        Stock stockValid = stockService.createStockIfAbsent("VAL2", "Validation 2", new BigDecimal("0.005"));
        assertEquals(new BigDecimal("0.01"), stockValid.getCurrentPrice());
        assertEquals(1, stockRepository.count());
        assertEquals(1, historyRepository.count());

        // Negative input -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                stockService.createStockIfAbsent("VAL3", "Validation 3", new BigDecimal("-10.00")));

        // Null input -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                stockService.createStockIfAbsent("VAL4", "Validation 4", null));

        // Exceeding DECIMAL(12,2) limit -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                stockService.createStockIfAbsent("VAL5", "Validation 5", new BigDecimal("10000000000.00")));
    }

    @Test
    void updateStockPrice_RoundingAndBoundaryValidation() {
        Stock stock = stockService.createStockIfAbsent("UPD1", "Update Co", new BigDecimal("100.00"));

        // 0.004 rounds to 0.00 -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                stockService.updateStockPrice("UPD1", new BigDecimal("0.004"), Instant.now()));

        // 0.005 rounds to 0.01 -> accepted
        Stock updated = stockService.updateStockPrice("UPD1", new BigDecimal("0.005"), Instant.now());
        assertEquals(new BigDecimal("0.01"), updated.getCurrentPrice());

        // Negative input -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                stockService.updateStockPrice("UPD1", new BigDecimal("-5.00"), Instant.now()));

        // Null input -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                stockService.updateStockPrice("UPD1", null, Instant.now()));

        // Exceeding DECIMAL(12,2) limit -> rejected
        assertThrows(IllegalArgumentException.class, () ->
                stockService.updateStockPrice("UPD1", new BigDecimal("10000000000.00"), Instant.now()));
    }
}
