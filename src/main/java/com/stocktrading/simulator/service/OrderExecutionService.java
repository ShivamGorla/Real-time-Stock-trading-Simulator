package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderResponse;

public interface OrderExecutionService {

    /**
     * Executes a pending order in a single database transaction after verifying and settling
     * funds and shares via the configured WalletPort and HoldingsPort.
     *
     * @param orderId the ID of the order to execute
     * @param username the username of the order owner
     * @return the updated OrderResponse with status EXECUTED
     * @throws com.stocktrading.simulator.exception.ResourceNotFoundException if order does not exist or belong to user
     * @throws IllegalStateException if the order is not in PENDING state
     * @throws com.stocktrading.simulator.exception.SettlementUnavailableException if wallet or holdings adapters are not available
     * @throws com.stocktrading.simulator.exception.InsufficientFundsException if BUY order lacks sufficient funds
     * @throws com.stocktrading.simulator.exception.InsufficientSharesException if SELL order lacks sufficient shares
     */
    OrderResponse executeOrder(Long orderId, String username);
}
