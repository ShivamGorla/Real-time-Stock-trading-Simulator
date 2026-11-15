package com.stocktrading.simulator.service;

import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class StockPriceSimulator {

    private static final Logger log = LoggerFactory.getLogger(StockPriceSimulator.class);
    private static final BigDecimal MIN_PRICE_FLOOR = new BigDecimal("1.00");

    private final StockRepository stockRepository;
    private final StockService stockService;

    @Value("${stock.simulation.enabled:true}")
    private boolean enabled;

    @Value("${stock.simulation.max-pct-change:0.02}")
    private double maxPctChange;

    public StockPriceSimulator(StockRepository stockRepository, StockService stockService) {
        this.stockRepository = stockRepository;
        this.stockService = stockService;
    }

    @Scheduled(fixedRateString = "${stock.simulation.fixed-rate:5000}", initialDelayString = "${stock.simulation.initial-delay:5000}")
    public synchronized void simulatePriceTicks() {
        if (!enabled) {
            return;
        }

        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            return;
        }

        Instant nowUtc = Instant.now();
        for (Stock stock : stocks) {
            try {
                BigDecimal currentPrice = stock.getCurrentPrice();
                BigDecimal newPrice = calculateNextPrice(currentPrice, maxPctChange);
                stockService.updateStockPrice(stock.getSymbol(), newPrice, nowUtc);
            } catch (Exception e) {
                log.warn("Failed to simulate price for stock {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
    }

    public BigDecimal calculateNextPrice(BigDecimal currentPrice, double maxChangeFactor) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return MIN_PRICE_FLOOR;
        }

        // Generate percentage change between -maxChangeFactor and +maxChangeFactor
        double randomFactor = ThreadLocalRandom.current().nextDouble(-maxChangeFactor, maxChangeFactor);
        BigDecimal deltaFactor = BigDecimal.valueOf(1.0 + randomFactor);
        BigDecimal calculated = currentPrice.multiply(deltaFactor).setScale(2, RoundingMode.HALF_UP);

        // Ensure price never falls below minimum floor
        return calculated.compareTo(MIN_PRICE_FLOOR) < 0 ? MIN_PRICE_FLOOR : calculated;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
