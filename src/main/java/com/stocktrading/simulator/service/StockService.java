package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.StockPriceHistoryResponse;
import com.stocktrading.simulator.dto.StockResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.model.StockPriceHistory;
import com.stocktrading.simulator.repository.StockPriceHistoryRepository;
import com.stocktrading.simulator.repository.StockRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockService {

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 200;
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

    private final StockRepository stockRepository;
    private final StockPriceHistoryRepository stockPriceHistoryRepository;

    public StockService(StockRepository stockRepository,
                        StockPriceHistoryRepository stockPriceHistoryRepository) {
        this.stockRepository = stockRepository;
        this.stockPriceHistoryRepository = stockPriceHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getAllStocks() {
        return stockRepository.findAllByOrderBySymbolAsc().stream()
                .map(StockResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StockResponse getStockBySymbol(String symbol) {
        Stock stock = findStockOrThrow(symbol);
        return new StockResponse(stock);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> searchStocks(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllStocks();
        }
        String cleanQuery = query.trim();
        return stockRepository.findBySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCaseOrderBySymbolAsc(cleanQuery, cleanQuery).stream()
                .map(StockResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockPriceHistoryResponse> getStockHistory(String symbol, Integer limit) {
        Stock stock = findStockOrThrow(symbol);
        int boundedLimit = (limit == null || limit <= 0) ? DEFAULT_HISTORY_LIMIT : Math.min(limit, MAX_HISTORY_LIMIT);

        List<StockPriceHistory> recentDesc = stockPriceHistoryRepository.findRecentHistoryByStock(
                stock, PageRequest.of(0, boundedLimit)
        );

        // Return in chronological order (oldest to newest)
        List<StockPriceHistory> chronological = new ArrayList<>(recentDesc);
        Collections.reverse(chronological);

        return chronological.stream()
                .map(StockPriceHistoryResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public synchronized Stock createStockIfAbsent(String symbol, String companyName, BigDecimal initialPrice) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Stock symbol cannot be blank");
        }
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name cannot be blank");
        }
        if (initialPrice == null || initialPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Initial stock price must be positive");
        }

        BigDecimal formattedPrice = initialPrice.setScale(2, RoundingMode.HALF_UP);
        if (formattedPrice.compareTo(MIN_PRICE) < 0) {
            throw new IllegalArgumentException("Initial stock price must be strictly positive after rounding");
        }
        if (formattedPrice.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Initial stock price exceeds maximum allowed limit of " + MAX_PRICE);
        }

        String normalizedSymbol = symbol.trim().toUpperCase();
        return stockRepository.findBySymbolIgnoreCase(normalizedSymbol).orElseGet(() -> {
            Instant nowUtc = Instant.now();
            Stock newStock = new Stock(normalizedSymbol, companyName.trim(), formattedPrice, nowUtc);
            Stock savedStock = stockRepository.save(newStock);
            stockPriceHistoryRepository.save(new StockPriceHistory(savedStock, formattedPrice, nowUtc));
            return savedStock;
        });
    }

    @Transactional
    public synchronized Stock updateStockPrice(String symbol, BigDecimal newPrice, Instant updateTime) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Stock price must be positive");
        }
        BigDecimal roundedPrice = newPrice.setScale(2, RoundingMode.HALF_UP);
        if (roundedPrice.compareTo(MIN_PRICE) < 0) {
            throw new IllegalArgumentException("Stock price must be strictly positive after rounding");
        }
        if (roundedPrice.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Stock price exceeds maximum allowed limit of " + MAX_PRICE);
        }
        Stock stock = findStockOrThrow(symbol);
        Instant timestamp = updateTime != null ? updateTime : Instant.now();

        stock.updatePrice(roundedPrice, timestamp);
        Stock savedStock = stockRepository.save(stock);
        stockPriceHistoryRepository.save(new StockPriceHistory(savedStock, roundedPrice, timestamp));
        return savedStock;
    }

    public Stock findStockOrThrow(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new ResourceNotFoundException("Stock symbol cannot be blank");
        }
        return stockRepository.findBySymbolIgnoreCase(symbol.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Stock with symbol '" + symbol.trim().toUpperCase() + "' not found"));
    }
}
