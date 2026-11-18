package com.stocktrading.simulator.assistant.client.exception;

/**
 * Thrown when the LLM provider experiences an internal server or service unavailable error (HTTP 5xx).
 */
public class LlmServerException extends LlmException {

    public LlmServerException(String message, Integer statusCode) {
        super(message, statusCode);
    }

    public LlmServerException(String message, Integer statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
