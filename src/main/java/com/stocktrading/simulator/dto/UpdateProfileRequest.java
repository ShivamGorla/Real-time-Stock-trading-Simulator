package com.stocktrading.simulator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The only user-editable profile field today. Username/email/public ID are deliberately absent. */
public record UpdateProfileRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    String name) {}
