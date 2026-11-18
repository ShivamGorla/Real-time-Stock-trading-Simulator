package com.stocktrading.simulator.assistant.client.exception;

/**
 * Thrown when the LLM provider rejects the request payload as invalid or malformed (HTTP 400 Bad Request or 404 Not Found).
 */
public class LlmBadRequestException extends LlmException {

    public LlmBadRequestException(String message, Integer statusCode) {
        super(message, statusCode);
    }

    public LlmBadRequestException(String message, Integer statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
