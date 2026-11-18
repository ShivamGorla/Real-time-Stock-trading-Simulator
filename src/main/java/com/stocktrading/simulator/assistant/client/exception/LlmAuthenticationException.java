package com.stocktrading.simulator.assistant.client.exception;

/**
 * Thrown when the LLM provider rejects authentication (HTTP 401 Unauthorized or 403 Forbidden).
 */
public class LlmAuthenticationException extends LlmException {

    public LlmAuthenticationException(String message, Integer statusCode) {
        super(message, statusCode);
    }

    public LlmAuthenticationException(String message, Integer statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
