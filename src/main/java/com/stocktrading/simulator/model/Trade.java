package com.stocktrading.simulator.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades", uniqueConstraints = {
    @UniqueConstraint(name = "uk_trades_order_id", columnNames = {"order_id"})
}, indexes = {
    @Index(name = "idx_trades_user_executed", columnList = "user_id, executed_at"),
    @Index(name = "idx_trades_stock_executed", columnList = "stock_id, executed_at")
})
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

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
    @Column(name = "execution_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal executionPrice;

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(name = "total_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalAmount;

    @NotNull
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected Trade() {}

    public Trade(Order order,
                 User user,
                 Stock stock,
                 OrderSide side,
                 Integer quantity,
                 BigDecimal executionPrice,
                 BigDecimal totalAmount,
                 Instant executedAt) {
        this.order = order;
        this.user = user;
        this.stock = stock;
        this.side = side;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.totalAmount = totalAmount;
        this.executedAt = executedAt != null ? executedAt : Instant.now();
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public User getUser() { return user; }
    public Stock getStock() { return stock; }
    public OrderSide getSide() { return side; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getExecutedAt() { return executedAt; }
}
