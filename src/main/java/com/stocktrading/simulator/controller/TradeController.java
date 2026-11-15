package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.TradeResponse;
import com.stocktrading.simulator.service.TradeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @GetMapping
    public ResponseEntity<Page<TradeResponse>> getUserTrades(
            @PageableDefault(size = 20, sort = "executedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(tradeService.getUserTrades(username, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TradeResponse> getTradeById(@PathVariable Long id,
                                                      Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(tradeService.getTradeById(username, id));
    }
}
