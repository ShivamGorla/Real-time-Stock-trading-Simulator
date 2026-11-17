package com.stocktrading.simulator.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "wallet_transactions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_wallet_txn_wallet_ref",
            columnNames = {"wallet_id", "reference_id"}
        )
    },
    indexes = {
        @Index(
            name = "idx_wallet_txn_wallet_created_id",
            columnList = "wallet_id, created_at DESC, id DESC"
        ),
        @Index(
            name = "idx_wallet_txn_wallet_type",
            columnList = "wallet_id, type"
        ),
        @Index(
            name = "idx_wallet_txn_wallet_category",
            columnList = "wallet_id, category"
        )
    }
)
@Immutable
@Check(constraints = "amount > 0 AND balance_after >= 0")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", referencedColumnName = "id", nullable = false, updatable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 10)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 20)
    private TransactionCategory category;

    @Column(name = "amount", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "reference_id", nullable = false, updatable = false, length = 64)
    private String referenceId;

    @Column(name = "description", updatable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransaction() {}

    public WalletTransaction(Wallet wallet,
                             TransactionType type,
                             TransactionCategory category,
                             BigDecimal amount,
                             BigDecimal balanceAfter,
                             String currency,
                             String referenceId,
                             String description) {
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("Transaction category cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be strictly positive");
        }
        if (balanceAfter == null || balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Resulting balanceAfter cannot be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Reference ID cannot be empty");
        }

        this.wallet = wallet;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.currency = currency;
        this.referenceId = referenceId;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
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
