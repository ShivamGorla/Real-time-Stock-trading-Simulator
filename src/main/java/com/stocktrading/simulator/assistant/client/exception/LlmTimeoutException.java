package com.stocktrading.simulator.assistant.client.exception;

/**
 * Thrown when an HTTP connection or read timeout occurs during communication with the LLM provider.
 */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
