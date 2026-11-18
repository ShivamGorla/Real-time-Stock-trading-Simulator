package com.stocktrading.simulator.assistant.tool.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetStockInformationArgs {

    private String symbol;

    public GetStockInformationArgs() {}

    public GetStockInformationArgs(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
