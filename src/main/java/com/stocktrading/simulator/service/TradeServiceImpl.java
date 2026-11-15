package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.TradeResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.Trade;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.TradeRepository;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeServiceImpl implements TradeService {

    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;

    public TradeServiceImpl(TradeRepository tradeRepository, UserRepository userRepository) {
        this.tradeRepository = tradeRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TradeResponse> getUserTrades(String username, Pageable pageable) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return tradeRepository.findByUserOrderByExecutedAtDesc(user, pageable)
                .map(TradeResponse::new);
    }

    @Override
    @Transactional(readOnly = true)
    public TradeResponse getTradeById(String username, Long tradeId) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (tradeId == null || tradeId <= 0) {
            throw new IllegalArgumentException("Trade ID must be positive");
        }
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Trade trade = tradeRepository.findByIdAndUser(tradeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Trade with id " + tradeId + " not found"));

        return new TradeResponse(trade);
    }
}
