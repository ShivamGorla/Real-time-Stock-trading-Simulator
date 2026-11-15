package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.StockPriceHistoryResponse;
import com.stocktrading.simulator.dto.StockResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.repository.OrderRepository;
import com.stocktrading.simulator.repository.StockPriceHistoryRepository;
import com.stocktrading.simulator.repository.StockRepository;
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
    private OrderRepository orderRepository;

    @Autowired
    private StockPriceSimulator stockPriceSimulator;

    @BeforeEach
    void setup() {
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
    void stockPriceSimulator_CalculateNextPrice_NeverBelowFloor() {
        BigDecimal verySmallPrice = new BigDecimal("0.50");
        BigDecimal nextPrice = stockPriceSimulator.calculateNextPrice(verySmallPrice, 0.50);

        assertTrue(nextPrice.compareTo(new BigDecimal("1.00")) >= 0,
                "Simulated price must stay positive and at or above floor");
    }
}
