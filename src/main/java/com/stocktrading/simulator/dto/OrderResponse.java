package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.Order;
import com.stocktrading.simulator.model.OrderSide;
import com.stocktrading.simulator.model.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public class OrderResponse {

    private Long id;
    private String clientOrderId;
    private String username;
    private String symbol;
    private String companyName;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal quotedPrice;
    private BigDecimal executionPrice;
    private BigDecimal totalQuotedAmount;
    private OrderStatus status;
    private Instant createdAt;
    private Instant executedAt;
    private boolean simulated;
    private String currency;
    private String note;

    public OrderResponse() {}

    public OrderResponse(Order order) {
        this.id = order.getId();
        this.clientOrderId = order.getClientOrderId();
        this.username = order.getUser().getUsername();
        this.symbol = order.getStock().getSymbol();
        this.companyName = order.getStock().getCompanyName();
        this.side = order.getSide();
        this.quantity = order.getQuantity();
        this.quotedPrice = order.getQuotedPrice().setScale(2, RoundingMode.HALF_UP);
        this.executionPrice = order.getExecutionPrice() != null ? order.getExecutionPrice().setScale(2, RoundingMode.HALF_UP) : null;
        this.totalQuotedAmount = order.getTotalQuotedAmount().setScale(2, RoundingMode.HALF_UP);
        this.status = order.getStatus();
        this.createdAt = order.getCreatedAt();
        this.executedAt = order.getExecutedAt();
        this.simulated = true;
        this.currency = "INR";
        this.note = "Quoted price is the market price snapshot at order submission time and is not a guaranteed execution price.";
    }

    public Long getId() { return id; }
    public String getClientOrderId() { return clientOrderId; }
    public String getUsername() { return username; }
    public String getSymbol() { return symbol; }
    public String getCompanyName() { return companyName; }
    public OrderSide getSide() { return side; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getQuotedPrice() { return quotedPrice; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public BigDecimal getTotalQuotedAmount() { return totalQuotedAmount; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExecutedAt() { return executedAt; }
    public boolean isSimulated() { return simulated; }
    public String getCurrency() { return currency; }
    public String getNote() { return note; }
}
