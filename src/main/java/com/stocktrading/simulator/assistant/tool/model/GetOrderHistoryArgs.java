package com.stocktrading.simulator.assistant.tool.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stocktrading.simulator.model.OrderSide;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetOrderHistoryArgs {

    private String type;
    private String symbol;
    private Integer page = 0;
    private Integer size = 20;

    public GetOrderHistoryArgs() {}

    public GetOrderHistoryArgs(String type, String symbol, Integer page, Integer size) {
        this.type = type;
        this.symbol = symbol;
        this.page = page;
        this.size = size;
    }

    public OrderSide resolveOrderSide() {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return OrderSide.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid order type filter: '" + type + "'. Allowed values: BUY, SELL");
        }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Integer getPage() { return page != null ? page : 0; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size != null ? size : 20; }
    public void setSize(Integer size) { this.size = size; }
}
