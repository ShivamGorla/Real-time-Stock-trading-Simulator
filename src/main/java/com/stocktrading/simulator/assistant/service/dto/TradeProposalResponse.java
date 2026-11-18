package com.stocktrading.simulator.assistant.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stocktrading.simulator.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Advisory DTO representing a proposed trade preview.
 * Strictly non-executable and non-binding. Contains calculated estimates only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeProposalResponse {

    private String symbol;
    private String companyName;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal estimatedPrice;
    private BigDecimal estimatedTotalAmount;
    private String currency;
    private BigDecimal currentWalletBalance;
    private Integer currentHoldingQuantity;
    private Boolean isExecutable;
    private BigDecimal estimatedBalanceAfter;
    private String validationMessage;
    private String advisoryNotice;
    private Instant generatedAt;

    public TradeProposalResponse() {}

    public TradeProposalResponse(String symbol,
                                 String companyName,
                                 OrderSide side,
                                 Integer quantity,
                                 BigDecimal estimatedPrice,
                                 BigDecimal estimatedTotalAmount,
                                 String currency,
                                 BigDecimal currentWalletBalance,
                                 Integer currentHoldingQuantity,
                                 Boolean isExecutable,
                                 BigDecimal estimatedBalanceAfter,
                                 String validationMessage,
                                 String advisoryNotice,
                                 Instant generatedAt) {
        this.symbol = symbol;
        this.companyName = companyName;
        this.side = side;
        this.quantity = quantity;
        this.estimatedPrice = estimatedPrice;
        this.estimatedTotalAmount = estimatedTotalAmount;
        this.currency = currency;
        this.currentWalletBalance = currentWalletBalance;
        this.currentHoldingQuantity = currentHoldingQuantity;
        this.isExecutable = isExecutable;
        this.estimatedBalanceAfter = estimatedBalanceAfter;
        this.validationMessage = validationMessage;
        this.advisoryNotice = advisoryNotice;
        this.generatedAt = generatedAt;
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

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getEstimatedPrice() {
        return estimatedPrice;
    }

    public void setEstimatedPrice(BigDecimal estimatedPrice) {
        this.estimatedPrice = estimatedPrice;
    }

    public BigDecimal getEstimatedTotalAmount() {
        return estimatedTotalAmount;
    }

    public void setEstimatedTotalAmount(BigDecimal estimatedTotalAmount) {
        this.estimatedTotalAmount = estimatedTotalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getCurrentWalletBalance() {
        return currentWalletBalance;
    }

    public void setCurrentWalletBalance(BigDecimal currentWalletBalance) {
        this.currentWalletBalance = currentWalletBalance;
    }

    public Integer getCurrentHoldingQuantity() {
        return currentHoldingQuantity;
    }

    public void setCurrentHoldingQuantity(Integer currentHoldingQuantity) {
        this.currentHoldingQuantity = currentHoldingQuantity;
    }

    public Boolean getIsExecutable() {
        return isExecutable;
    }

    public void setIsExecutable(Boolean isExecutable) {
        this.isExecutable = isExecutable;
    }

    public BigDecimal getEstimatedBalanceAfter() {
        return estimatedBalanceAfter;
    }

    public void setEstimatedBalanceAfter(BigDecimal estimatedBalanceAfter) {
        this.estimatedBalanceAfter = estimatedBalanceAfter;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public void setValidationMessage(String validationMessage) {
        this.validationMessage = validationMessage;
    }

    public String getAdvisoryNotice() {
        return advisoryNotice;
    }

    public void setAdvisoryNotice(String advisoryNotice) {
        this.advisoryNotice = advisoryNotice;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
