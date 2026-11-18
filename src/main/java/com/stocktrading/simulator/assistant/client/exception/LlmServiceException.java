package com.stocktrading.simulator.assistant.client.exception;

/**
 * Thrown on transport errors, invalid or unparseable JSON payloads, or missing response choices.
 */
public class LlmServiceException extends LlmException {

    public LlmServiceException(String message) {
        super(message);
    }

    public LlmServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmServiceException(String message, Integer statusCode) {
        super(message, statusCode);
    }

    public LlmServiceException(String message, Integer statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
