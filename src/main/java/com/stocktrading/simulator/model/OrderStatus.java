package com.stocktrading.simulator.model;

public enum OrderStatus {
    PENDING,
    EXECUTED,
    CANCELLED,
    REJECTED;

    public boolean isTerminal() {
        return this == EXECUTED || this == CANCELLED || this == REJECTED;
    }
}
