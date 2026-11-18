package com.stocktrading.simulator.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Only the amount comes from the client. Outcome, status and balance are decided server-side. */
public record TopUpRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places")
    BigDecimal amount) {}
