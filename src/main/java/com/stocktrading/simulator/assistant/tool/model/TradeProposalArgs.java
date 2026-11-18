package com.stocktrading.simulator.assistant.tool.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stocktrading.simulator.model.OrderSide;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeProposalArgs {

    private String symbol;
    private String side;
    private Integer quantity;

    public TradeProposalArgs() {}

    public TradeProposalArgs(String symbol, String side, Integer quantity) {
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
    }

    public OrderSide resolveOrderSide() {
        if (side == null || side.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter 'side' (BUY or SELL)");
        }
        try {
            return OrderSide.valueOf(side.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid order side: '" + side + "'. Must be BUY or SELL");
        }
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
