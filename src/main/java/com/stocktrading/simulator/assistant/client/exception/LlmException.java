package com.stocktrading.simulator.assistant.client.exception;

/**
 * Base unchecked exception for all LLM client and communication errors.
 */
public abstract class LlmException extends RuntimeException {

    private final Integer statusCode;

    protected LlmException(String message) {
        super(message);
        this.statusCode = null;
    }

    protected LlmException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    protected LlmException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    protected LlmException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
