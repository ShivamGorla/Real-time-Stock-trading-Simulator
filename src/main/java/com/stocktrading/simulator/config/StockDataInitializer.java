package com.stocktrading.simulator.config;

import com.stocktrading.simulator.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Order(1)
public class StockDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StockDataInitializer.class);

    private final StockService stockService;

    public StockDataInitializer(StockService stockService) {
        this.stockService = stockService;
    }

    private record SeedStock(String symbol, String companyName, BigDecimal price) {}

    private static final List<SeedStock> DEFAULT_STOCKS = List.of(
            new SeedStock("RELIANCE", "Reliance Industries Ltd", new BigDecimal("2950.00")),
            new SeedStock("TCS", "Tata Consultancy Services Ltd", new BigDecimal("3850.00")),
            new SeedStock("HDFCBANK", "HDFC Bank Ltd", new BigDecimal("1620.00")),
            new SeedStock("INFY", "Infosys Ltd", new BigDecimal("1540.00")),
            new SeedStock("ICICIBANK", "ICICI Bank Ltd", new BigDecimal("1110.00")),
            new SeedStock("TATAMOTORS", "Tata Motors Ltd", new BigDecimal("980.00")),
            new SeedStock("ITC", "ITC Ltd", new BigDecimal("435.00")),
            new SeedStock("SBIN", "State Bank of India", new BigDecimal("790.00")),
            new SeedStock("BHARTIARTL", "Bharti Airtel Ltd", new BigDecimal("1320.00")),
            new SeedStock("WIPRO", "Wipro Ltd", new BigDecimal("490.00"))
    );

    @Override
    public void run(String... args) {
        log.info("Checking and seeding simulated stocks...");
        for (SeedStock seed : DEFAULT_STOCKS) {
            stockService.createStockIfAbsent(seed.symbol(), seed.companyName(), seed.price());
        }
        log.info("Simulated stock catalog verified ({} default symbols ensured).", DEFAULT_STOCKS.size());
    }
}
