package com.stocktrading.simulator.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_price_history", indexes = {
    @Index(name = "idx_history_stock_recorded_at", columnList = "stock_id, recorded_at")
})
public class StockPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected StockPriceHistory() {}

    public StockPriceHistory(Stock stock, BigDecimal price, Instant recordedAt) {
        this.stock = stock;
        this.price = price;
        this.recordedAt = recordedAt != null ? recordedAt : Instant.now();
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public BigDecimal getPrice() { return price; }
    public Instant getRecordedAt() { return recordedAt; }
}
