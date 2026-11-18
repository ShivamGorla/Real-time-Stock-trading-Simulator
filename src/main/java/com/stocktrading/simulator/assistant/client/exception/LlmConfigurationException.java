package com.stocktrading.simulator.assistant.client.exception;

/**
 * Thrown when the LLM assistant is disabled or misconfigured (e.g. missing API key or invalid URL).
 */
public class LlmConfigurationException extends LlmException {

    public LlmConfigurationException(String message) {
        super(message);
    }

    public LlmConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
