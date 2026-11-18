package com.stocktrading.simulator.assistant.client.exception;

/**
 * Thrown when the LLM provider rate limit is exceeded (HTTP 429 Too Many Requests).
 */
public class LlmRateLimitException extends LlmException {

    public LlmRateLimitException(String message, Integer statusCode) {
        super(message, statusCode);
    }

    public LlmRateLimitException(String message, Integer statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
