package com.stocktrading.simulator.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stocks", indexes = {
    @Index(name = "idx_stock_symbol", columnList = "symbol", unique = true)
})
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 20)
    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @NotBlank
    @Size(max = 100)
    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "previous_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal previousPrice;

    @NotNull
    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected Stock() {}

    public Stock(String symbol, String companyName, BigDecimal initialPrice) {
        this(symbol, companyName, initialPrice, Instant.now());
    }

    public Stock(String symbol, String companyName, BigDecimal initialPrice, Instant timestamp) {
        this.symbol = symbol.trim().toUpperCase();
        this.companyName = companyName.trim();
        this.currentPrice = initialPrice;
        this.previousPrice = initialPrice;
        this.lastUpdated = timestamp != null ? timestamp : Instant.now();
    }

    public void updatePrice(BigDecimal newPrice) {
        updatePrice(newPrice, Instant.now());
    }

    public void updatePrice(BigDecimal newPrice, Instant updateTime) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Simulated stock price must be strictly positive");
        }
        this.previousPrice = this.currentPrice;
        this.currentPrice = newPrice;
        this.lastUpdated = updateTime != null ? updateTime : Instant.now();
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol != null ? symbol.trim().toUpperCase() : null; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public BigDecimal getPreviousPrice() { return previousPrice; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Long getVersion() { return version; }
}
