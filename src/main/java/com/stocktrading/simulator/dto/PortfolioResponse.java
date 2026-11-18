package com.stocktrading.simulator.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PortfolioResponse {

    private Long userId;
    private String username;
    private BigDecimal walletBalance;
    private String currency;
    private BigDecimal totalInvestedValue;
    private BigDecimal totalCurrentMarketValue;
    private BigDecimal totalUnrealizedPnl;
    private BigDecimal totalRealizedPnl;
    private BigDecimal totalPnl;
    private BigDecimal unrealizedReturnPercentage;
    private BigDecimal totalAccountEquity;
    private Instant generatedAt;
    private List<HoldingResponse> holdings;

    public PortfolioResponse() {}

    public PortfolioResponse(Long userId,
                             String username,
                             BigDecimal walletBalance,
                             String currency,
                             BigDecimal totalInvestedValue,
                             BigDecimal totalCurrentMarketValue,
                             BigDecimal totalUnrealizedPnl,
                             BigDecimal totalRealizedPnl,
                             BigDecimal totalPnl,
                             BigDecimal unrealizedReturnPercentage,
                             BigDecimal totalAccountEquity,
                             Instant generatedAt,
                             List<HoldingResponse> holdings) {
        this.userId = userId;
        this.username = username;
        this.walletBalance = walletBalance;
        this.currency = currency;
        this.totalInvestedValue = totalInvestedValue;
        this.totalCurrentMarketValue = totalCurrentMarketValue;
        this.totalUnrealizedPnl = totalUnrealizedPnl;
        this.totalRealizedPnl = totalRealizedPnl;
        this.totalPnl = totalPnl;
        this.unrealizedReturnPercentage = unrealizedReturnPercentage;
        this.totalAccountEquity = totalAccountEquity;
        this.generatedAt = generatedAt;
        this.holdings = holdings;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(BigDecimal walletBalance) {
        this.walletBalance = walletBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTotalInvestedValue() {
        return totalInvestedValue;
    }

    public void setTotalInvestedValue(BigDecimal totalInvestedValue) {
        this.totalInvestedValue = totalInvestedValue;
    }

    public BigDecimal getTotalCurrentMarketValue() {
        return totalCurrentMarketValue;
    }

    public void setTotalCurrentMarketValue(BigDecimal totalCurrentMarketValue) {
        this.totalCurrentMarketValue = totalCurrentMarketValue;
    }

    public BigDecimal getTotalUnrealizedPnl() {
        return totalUnrealizedPnl;
    }

    public void setTotalUnrealizedPnl(BigDecimal totalUnrealizedPnl) {
        this.totalUnrealizedPnl = totalUnrealizedPnl;
    }

    public BigDecimal getTotalRealizedPnl() {
        return totalRealizedPnl;
    }

    public void setTotalRealizedPnl(BigDecimal totalRealizedPnl) {
        this.totalRealizedPnl = totalRealizedPnl;
    }

    public BigDecimal getTotalPnl() {
        return totalPnl;
    }

    public void setTotalPnl(BigDecimal totalPnl) {
        this.totalPnl = totalPnl;
    }

    public BigDecimal getUnrealizedReturnPercentage() {
        return unrealizedReturnPercentage;
    }

    public void setUnrealizedReturnPercentage(BigDecimal unrealizedReturnPercentage) {
        this.unrealizedReturnPercentage = unrealizedReturnPercentage;
    }

    public BigDecimal getTotalAccountEquity() {
        return totalAccountEquity;
    }

    public void setTotalAccountEquity(BigDecimal totalAccountEquity) {
        this.totalAccountEquity = totalAccountEquity;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<HoldingResponse> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<HoldingResponse> holdings) {
        this.holdings = holdings;
    }
}
