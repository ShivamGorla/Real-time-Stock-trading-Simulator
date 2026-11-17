package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.TransactionType;
import com.stocktrading.simulator.model.WalletTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public class WalletTransactionResponse {

    private final Long id;
    private final Long walletId;
    private final TransactionType type;
    private final TransactionCategory category;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String currency;
    private final String referenceId;
    private final String description;
    private final Instant createdAt;

    public WalletTransactionResponse(WalletTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        this.id = transaction.getId();
        this.walletId = transaction.getWallet() != null ? transaction.getWallet().getId() : null;
        this.type = transaction.getType();
        this.category = transaction.getCategory();
        this.amount = transaction.getAmount();
        this.balanceAfter = transaction.getBalanceAfter();
        this.currency = transaction.getCurrency();
        this.referenceId = transaction.getReferenceId();
        this.description = transaction.getDescription();
        this.createdAt = transaction.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public Long getWalletId() {
        return walletId;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
