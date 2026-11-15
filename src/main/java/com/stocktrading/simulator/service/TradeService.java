package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.TradeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TradeService {

    /**
     * Retrieves paginated trades executed for the specified authenticated user.
     *
     * @param username the username of the authenticated user
     * @param pageable pagination and sorting parameters
     * @return a page of TradeResponse DTOs
     */
    Page<TradeResponse> getUserTrades(String username, Pageable pageable);

    /**
     * Retrieves an individual trade by ID for the specified authenticated user.
     *
     * @param username the username of the authenticated user
     * @param tradeId the ID of the trade
     * @return the TradeResponse DTO
     * @throws com.stocktrading.simulator.exception.ResourceNotFoundException if trade does not exist or belong to user
     */
    TradeResponse getTradeById(String username, Long tradeId);
}
