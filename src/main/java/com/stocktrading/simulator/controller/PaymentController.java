package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.PaymentResponse;
import com.stocktrading.simulator.dto.TopUpRequest;
import com.stocktrading.simulator.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Simulated top-ups. Requires login (see SecurityConfig); every query is scoped to the caller. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/top-up")
    public ResponseEntity<PaymentResponse> topUp(@Valid @RequestBody TopUpRequest request,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 Authentication authentication) {
        return ResponseEntity.ok(paymentService.topUp(authentication.getName(), idempotencyKey, request.amount()));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentResponse>> history(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         Authentication authentication) {
        return ResponseEntity.ok(paymentService.history(authentication.getName(), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(paymentService.get(authentication.getName(), id));
    }
}
