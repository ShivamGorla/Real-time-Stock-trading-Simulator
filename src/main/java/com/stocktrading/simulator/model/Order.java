package com.stocktrading.simulator.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_client_order_id", columnNames = {"user_id", "client_order_id"})
}, indexes = {
    @Index(name = "idx_orders_user_created", columnList = "user_id, created_at")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "client_order_id", nullable = false, length = 64)
    private String clientOrderId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "quoted_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal quotedPrice;

    @DecimalMin(value = "0.01")
    @Column(name = "execution_price", precision = 12, scale = 2)
    private BigDecimal executionPrice;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "total_quoted_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalQuotedAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected Order() {}

    public Order(String clientOrderId,
                 User user,
                 Stock stock,
                 OrderSide side,
                 Integer quantity,
                 BigDecimal quotedPrice,
                 BigDecimal totalQuotedAmount,
                 Instant createdAt) {
        this.clientOrderId = clientOrderId != null ? clientOrderId.trim() : null;
        this.user = user;
        this.stock = stock;
        this.side = side;
        this.quantity = quantity;
        this.quotedPrice = quotedPrice;
        this.totalQuotedAmount = totalQuotedAmount;
        this.status = OrderStatus.PENDING;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.executedAt = null;
        this.executionPrice = null;
    }

    public void transitionTo(OrderStatus newStatus, BigDecimal executionPrice, Instant executionTime) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException("Cannot transition order from terminal status " + this.status);
        }
        if (newStatus == OrderStatus.EXECUTED) {
            if (executionPrice == null || executionPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Execution price must be strictly positive");
            }
            this.executionPrice = executionPrice;
            this.executedAt = executionTime != null ? executionTime : Instant.now();
        }
        this.status = newStatus;
    }

    public Long getId() { return id; }
    public String getClientOrderId() { return clientOrderId; }
    public User getUser() { return user; }
    public Stock getStock() { return stock; }
    public OrderSide getSide() { return side; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getQuotedPrice() { return quotedPrice; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public BigDecimal getTotalQuotedAmount() { return totalQuotedAmount; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExecutedAt() { return executedAt; }
    public Long getVersion() { return version; }
}
