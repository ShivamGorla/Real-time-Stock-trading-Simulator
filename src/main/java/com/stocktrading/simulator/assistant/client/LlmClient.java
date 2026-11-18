package com.stocktrading.simulator.assistant.client;

import com.stocktrading.simulator.assistant.client.exception.LlmException;
import com.stocktrading.simulator.assistant.client.model.ChatCompletionRequest;
import com.stocktrading.simulator.assistant.client.model.ChatCompletionResponse;

/**
 * Interface abstracting communication with an underlying Large Language Model provider.
 * Follows the standard OpenAI-compatible Chat Completions protocol.
 */
public interface LlmClient {

    /**
     * Executes a synchronous chat completion call against the configured LLM provider.
     *
     * @param request the chat completion request payload
     * @return the structured response payload returned by the model
     * @throws LlmException upon provider error, timeout, authentication failure, or malformed response
     */
    ChatCompletionResponse chatCompletion(ChatCompletionRequest request);

    /**
     * Indicates whether the assistant is actively enabled and configured with valid credentials.
     *
     * @return true if enabled and configured; false otherwise
     */
    boolean isEnabled();
}
