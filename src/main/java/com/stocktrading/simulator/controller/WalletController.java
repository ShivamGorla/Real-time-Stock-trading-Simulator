package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.WalletResponse;
import com.stocktrading.simulator.dto.WalletTransactionResponse;
import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.TransactionType;
import com.stocktrading.simulator.service.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ResponseEntity<WalletResponse> getWallet(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WalletResponse walletResponse = walletService.getWalletResponseByUsername(authentication.getName());
        return ResponseEntity.ok(walletResponse);
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<WalletTransactionResponse>> getTransactions(
            Authentication authentication,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (startDate != null && endDate != null && !startDate.isBefore(endDate)) {
            throw new IllegalArgumentException("startDate must be strictly before endDate");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from date-time must be strictly before to date-time");
        }

        Instant fromInstant = from;
        Instant toInstant = to;

        if (startDate != null && fromInstant == null) {
            fromInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        if (endDate != null && toInstant == null) {
            toInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        Page<WalletTransactionResponse> history = walletService.getTransactionHistory(
            authentication.getName(),
            type,
            category,
            fromInstant,
            toInstant,
            pageable
        );

        return ResponseEntity.ok(history);
    }
}
