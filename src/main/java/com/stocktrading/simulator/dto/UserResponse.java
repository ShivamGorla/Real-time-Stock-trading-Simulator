package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class UserResponse {
    private Long id;
    private Integer publicUserId;
    private String name;
    private String username;
    private String email;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private BigDecimal virtualBalance;

    public UserResponse(User user, BigDecimal virtualBalance) {
        this.id = user.getId();
        this.publicUserId = user.getPublicUserId();
        this.name = user.getName();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.emailVerified = user.isEmailVerifiedOrLegacy();
        this.createdAt = user.getCreatedAt();
        this.virtualBalance = virtualBalance != null ? virtualBalance : BigDecimal.ZERO;
    }

    public UserResponse(User user) {
        this(user, BigDecimal.ZERO);
    }

    public Long getId() { return id; }
    public Integer getPublicUserId() { return publicUserId; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BigDecimal getVirtualBalance() { return virtualBalance; }
}
