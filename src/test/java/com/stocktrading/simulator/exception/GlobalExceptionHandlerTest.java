package com.stocktrading.simulator.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInsufficientBalance_Returns400BadRequest() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Not enough money");
        ResponseEntity<Map<String, String>> response = handler.handleInsufficientBalance(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Not enough money", response.getBody().get("error"));
    }

    @Test
    void handleIdempotencyConflict_Returns409Conflict() {
        IdempotencyConflictException ex = new IdempotencyConflictException("Reference ID already exists");
        ResponseEntity<Map<String, String>> response = handler.handleIdempotencyConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Reference ID already exists", response.getBody().get("error"));
    }

    @Test
    void handleOptimisticLocking_Returns409Conflict() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Wallet", 1L);
        ResponseEntity<Map<String, String>> response = handler.handleOptimisticLocking(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Concurrent modification detected. Please retry the operation.", response.getBody().get("error"));
    }

    @Test
    void handleDeadlock_Returns503ServiceUnavailable() {
        CannotAcquireLockException ex = new CannotAcquireLockException("Deadlock detected");
        ResponseEntity<Map<String, String>> response = handler.handleDeadlock(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Database deadlock detected. Please retry the operation.", response.getBody().get("error"));
    }

    @Test
    void handleLockTimeout_Returns503ServiceUnavailable() {
        PessimisticLockingFailureException ex = new PessimisticLockingFailureException("Lock wait timeout");
        ResponseEntity<Map<String, String>> response = handler.handleLockTimeout(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Database lock timeout. Please retry the operation.", response.getBody().get("error"));
    }
}
