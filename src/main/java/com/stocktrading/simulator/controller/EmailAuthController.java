package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.dto.EmailRequest;
import com.stocktrading.simulator.dto.ResetPasswordRequest;
import com.stocktrading.simulator.service.EmailAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class EmailAuthController {

    private static final Map<String, String> GENERIC =
        Map.of("message", "If an account matches, an email is on its way.");

    private final EmailAuthService service;

    public EmailAuthController(EmailAuthService service) {
        this.service = service;
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam("token") String token) {
        service.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified. You can sign in."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody EmailRequest request) {
        service.resendVerification(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GENERIC);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody EmailRequest request) {
        service.forgotPassword(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GENERIC);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password updated. You can sign in."));
    }
}
