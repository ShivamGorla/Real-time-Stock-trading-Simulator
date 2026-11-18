package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, BigDecimal amount, String status, boolean simulated,
                              Instant createdAt, Instant completedAt, String failureReason) {

    public static PaymentResponse from(PaymentTransaction p) {
        return new PaymentResponse(p.getId(), p.getAmount(), p.getStatus().name(), true,
            p.getCreatedAt(), p.getCompletedAt(), p.getFailureReason());
    }
}
