package com.stocktrading.simulator.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class HoldingResponse {

    private Long stockId;
    private String symbol;
    private String companyName;
    private Integer quantity;
    private BigDecimal averageCost;
    private BigDecimal currentPrice;
    private BigDecimal investedValue;
    private BigDecimal currentMarketValue;
    private BigDecimal unrealizedPnl;
    private BigDecimal unrealizedReturnPercentage;
    private BigDecimal cumulativeRealizedPnl;
    private Instant updatedAt;

    public HoldingResponse() {}

    public HoldingResponse(Long stockId,
                           String symbol,
                           String companyName,
                           Integer quantity,
                           BigDecimal averageCost,
                           BigDecimal currentPrice,
                           BigDecimal investedValue,
                           BigDecimal currentMarketValue,
                           BigDecimal unrealizedPnl,
                           BigDecimal unrealizedReturnPercentage,
                           BigDecimal cumulativeRealizedPnl,
                           Instant updatedAt) {
        this.stockId = stockId;
        this.symbol = symbol;
        this.companyName = companyName;
        this.quantity = quantity;
        this.averageCost = averageCost;
        this.currentPrice = currentPrice;
        this.investedValue = investedValue;
        this.currentMarketValue = currentMarketValue;
        this.unrealizedPnl = unrealizedPnl;
        this.unrealizedReturnPercentage = unrealizedReturnPercentage;
        this.cumulativeRealizedPnl = cumulativeRealizedPnl;
        this.updatedAt = updatedAt;
    }

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getInvestedValue() {
        return investedValue;
    }

    public void setInvestedValue(BigDecimal investedValue) {
        this.investedValue = investedValue;
    }

    public BigDecimal getCurrentMarketValue() {
        return currentMarketValue;
    }

    public void setCurrentMarketValue(BigDecimal currentMarketValue) {
        this.currentMarketValue = currentMarketValue;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
        this.unrealizedPnl = unrealizedPnl;
    }

    public BigDecimal getUnrealizedReturnPercentage() {
        return unrealizedReturnPercentage;
    }

    public void setUnrealizedReturnPercentage(BigDecimal unrealizedReturnPercentage) {
        this.unrealizedReturnPercentage = unrealizedReturnPercentage;
    }

    public BigDecimal getCumulativeRealizedPnl() {
        return cumulativeRealizedPnl;
    }

    public void setCumulativeRealizedPnl(BigDecimal cumulativeRealizedPnl) {
        this.cumulativeRealizedPnl = cumulativeRealizedPnl;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
