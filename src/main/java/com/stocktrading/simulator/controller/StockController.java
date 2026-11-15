package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.StockPriceHistoryResponse;
import com.stocktrading.simulator.dto.StockResponse;
import com.stocktrading.simulator.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public ResponseEntity<List<StockResponse>> getAllStocks() {
        return ResponseEntity.ok(stockService.getAllStocks());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<StockResponse> getStockBySymbol(@PathVariable String symbol) {
        return ResponseEntity.ok(stockService.getStockBySymbol(symbol));
    }

    @GetMapping("/search")
    public ResponseEntity<List<StockResponse>> searchStocks(@RequestParam(name = "query", required = false) String query) {
        return ResponseEntity.ok(stockService.searchStocks(query));
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<List<StockPriceHistoryResponse>> getStockHistory(
            @PathVariable String symbol,
            @RequestParam(name = "limit", required = false, defaultValue = "50") Integer limit) {
        return ResponseEntity.ok(stockService.getStockHistory(symbol, limit));
    }
}
