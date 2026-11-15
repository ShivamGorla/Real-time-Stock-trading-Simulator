package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.StockPriceHistory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class StockPriceHistoryResponse {

    private BigDecimal price;
    private Instant recordedAt;
    private boolean simulated;

    public StockPriceHistoryResponse() {}

    public StockPriceHistoryResponse(StockPriceHistory history) {
        this.price = history.getPrice().setScale(2, RoundingMode.HALF_UP);
        this.recordedAt = history.getRecordedAt();
        this.simulated = true;
    }

    public StockPriceHistoryResponse(BigDecimal price, Instant recordedAt) {
        this.price = price != null ? price.setScale(2, RoundingMode.HALF_UP) : null;
        this.recordedAt = recordedAt;
        this.simulated = true;
    }

    public BigDecimal getPrice() { return price; }
    public Instant getRecordedAt() { return recordedAt; }
    public boolean isSimulated() { return simulated; }
}
