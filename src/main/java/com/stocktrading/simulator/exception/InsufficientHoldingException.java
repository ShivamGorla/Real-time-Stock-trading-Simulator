package com.stocktrading.simulator.exception;

/**
 * Thrown when an order execution or sell operation requests more shares than are currently held in a holding.
 */
public class InsufficientHoldingException extends InsufficientSharesException {

    public InsufficientHoldingException(String message) {
        super(message);
    }
}
