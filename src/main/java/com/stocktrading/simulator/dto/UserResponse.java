package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserResponse {
    private Long id;
    private String name;
    private String username;
    private String email;
    private LocalDateTime createdAt;
    private BigDecimal virtualBalance;

    public UserResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.createdAt = user.getCreatedAt();
        // Server-controlled starting balance for Phase 2 dashboard
        this.virtualBalance = new BigDecimal("100000.00");
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BigDecimal getVirtualBalance() { return virtualBalance; }
}
