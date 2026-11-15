package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.OrderSide;
import com.stocktrading.simulator.model.Trade;

import java.math.BigDecimal;
import java.time.Instant;

public class TradeResponse {

    private Long id;
    private Long orderId;
    private String clientOrderId;
    private String symbol;
    private String companyName;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal executionPrice;
    private BigDecimal totalAmount;
    private Instant executedAt;

    public TradeResponse() {}

    public TradeResponse(Trade trade) {
        this.id = trade.getId();
        this.orderId = trade.getOrder().getId();
        this.clientOrderId = trade.getOrder().getClientOrderId();
        this.symbol = trade.getStock().getSymbol();
        this.companyName = trade.getStock().getCompanyName();
        this.side = trade.getSide();
        this.quantity = trade.getQuantity();
        this.executionPrice = trade.getExecutionPrice();
        this.totalAmount = trade.getTotalAmount();
        this.executedAt = trade.getExecutedAt();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getSymbol() { return symbol; }
    public String getCompanyName() { return companyName; }
    public OrderSide getSide() { return side; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getExecutionPrice() { return executionPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getExecutedAt() { return executedAt; }
}
