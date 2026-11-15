package com.stocktrading.simulator.exception;

public class SettlementUnavailableException extends RuntimeException {
    public SettlementUnavailableException(String message) {
        super(message);
    }
}
