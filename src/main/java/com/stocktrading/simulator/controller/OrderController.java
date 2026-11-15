package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/buy")
    public ResponseEntity<OrderResponse> placeBuyOrder(@Valid @RequestBody OrderRequest request,
                                                       Authentication authentication) {
        String username = authentication.getName();
        OrderResponse response = orderService.createBuyOrder(username, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/sell")
    public ResponseEntity<OrderResponse> placeSellOrder(@Valid @RequestBody OrderRequest request,
                                                        Authentication authentication) {
        String username = authentication.getName();
        OrderResponse response = orderService.createSellOrder(username, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getUserOrders(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(orderService.getUserOrders(username));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getUserOrderById(@PathVariable Long id,
                                                          Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(orderService.getUserOrderById(username, id));
    }
}
