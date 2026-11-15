package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.TradeResponse;
import com.stocktrading.simulator.exception.ResourceNotFoundException;
import com.stocktrading.simulator.model.Trade;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.TradeRepository;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TradeServiceImpl implements TradeService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "executedAt", "executionPrice", "totalAmount", "quantity", "side"
    );

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

        Pageable sanitizedPageable = sanitizePageable(pageable);
        return tradeRepository.findByUser(user, sanitizedPageable)
                .map(TradeResponse::new);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "executedAt"));
        }

        int page = Math.max(0, pageable.getPageNumber());
        int rawSize = pageable.getPageSize();
        int size = rawSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(rawSize, MAX_PAGE_SIZE);

        Sort safeSort = sanitizeSort(pageable.getSort());
        return PageRequest.of(page, size, safeSort);
    }

    private Sort sanitizeSort(Sort requestedSort) {
        if (requestedSort == null || requestedSort.isUnsorted()) {
            return Sort.by(Sort.Direction.DESC, "executedAt");
        }

        List<Sort.Order> safeOrders = new ArrayList<>();
        for (Sort.Order order : requestedSort) {
            if (ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                safeOrders.add(order);
            }
        }

        if (safeOrders.isEmpty()) {
            return Sort.by(Sort.Direction.DESC, "executedAt");
        }

        return Sort.by(safeOrders);
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
