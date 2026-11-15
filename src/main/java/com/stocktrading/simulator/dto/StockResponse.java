package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.Stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class StockResponse {

    private String symbol;
    private String companyName;
    private BigDecimal currentPrice;
    private BigDecimal previousPrice;
    private BigDecimal change;
    private BigDecimal changePercent;
    private Instant lastUpdated;
    private boolean simulated;
    private String currency;

    public StockResponse() {}

    public StockResponse(Stock stock) {
        this.symbol = stock.getSymbol();
        this.companyName = stock.getCompanyName();
        this.currentPrice = stock.getCurrentPrice().setScale(2, RoundingMode.HALF_UP);
        this.previousPrice = stock.getPreviousPrice().setScale(2, RoundingMode.HALF_UP);
        this.change = this.currentPrice.subtract(this.previousPrice).setScale(2, RoundingMode.HALF_UP);

        if (this.previousPrice.compareTo(BigDecimal.ZERO) > 0) {
            this.changePercent = this.change
                    .multiply(BigDecimal.valueOf(100))
                    .divide(this.previousPrice, 2, RoundingMode.HALF_UP);
        } else {
            this.changePercent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        this.lastUpdated = stock.getLastUpdated();
        this.simulated = true;
        this.currency = "INR";
    }

    public String getSymbol() { return symbol; }
    public String getCompanyName() { return companyName; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getPreviousPrice() { return previousPrice; }
    public BigDecimal getChange() { return change; }
    public BigDecimal getChangePercent() { return changePercent; }
    public Instant getLastUpdated() { return lastUpdated; }
    public boolean isSimulated() { return simulated; }
    public String getCurrency() { return currency; }
}
