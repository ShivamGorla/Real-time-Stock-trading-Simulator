package com.stocktrading.simulator.dto;

import com.stocktrading.simulator.model.Wallet;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WalletResponse {

    private Long walletId;
    private Long userId;
    private String username;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime updatedAt;

    public WalletResponse() {}

    public WalletResponse(Wallet wallet) {
        this.walletId = wallet.getId();
        this.userId = wallet.getUser().getId();
        this.username = wallet.getUser().getUsername();
        this.balance = wallet.getBalance();
        this.currency = wallet.getCurrency();
        this.updatedAt = wallet.getUpdatedAt();
    }

    public Long getWalletId() { return walletId; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
