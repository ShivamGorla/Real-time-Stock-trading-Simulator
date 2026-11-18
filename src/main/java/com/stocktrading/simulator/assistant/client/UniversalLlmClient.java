package com.stocktrading.simulator.assistant.client;

import com.stocktrading.simulator.assistant.client.exception.*;
import com.stocktrading.simulator.assistant.client.model.ChatCompletionRequest;
import com.stocktrading.simulator.assistant.client.model.ChatCompletionResponse;
import com.stocktrading.simulator.assistant.config.AssistantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Universal OpenAI-compatible implementation of {@link LlmClient} built using Spring's native {@link RestClient}.
 * Encapsulates HTTP communication, header injection, timeouts, error classification, and safe deserialization.
 */
public class UniversalLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(UniversalLlmClient.class);

    private final AssistantProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public UniversalLlmClient(AssistantProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public boolean isEnabled() {
        return properties != null && properties.isEnabled() && restClient != null;
    }

    @Override
    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        if (!isEnabled()) {
            throw new LlmConfigurationException(
                "StockSim AI Assistant is currently disabled. Set 'stocksim.assistant.enabled=true' with valid credentials to enable."
            );
        }

        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("ChatCompletionRequest must contain at least one message");
        }

        // Apply configured defaults if not specified on the request
        if (request.getModel() == null || request.getModel().isBlank()) {
            request.setModel(properties.getModel());
        }
        if (request.getTemperature() == null) {
            request.setTemperature(properties.getTemperature());
        }
        if (request.getMaxTokens() == null) {
            request.setMaxTokens(properties.getMaxTokens());
        }

        // Log request metadata only — never log prompt content, secrets, or financial payloads
        log.info("Issuing LLM chat completion request. Model: {}, Messages count: {}, Tools count: {}",
                request.getModel(),
                request.getMessages().size(),
                request.getTools() != null ? request.getTools().size() : 0);

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        String responseBody = new String(res.getBody().readAllBytes());
                        String sanitizedError = extractErrorMessage(responseBody, status);

                        if (status == 401 || status == 403) {
                            log.warn("LLM provider authentication failed (HTTP {})", status);
                            throw new LlmAuthenticationException(
                                "LLM provider authentication failed (HTTP " + status + "). Check API key.",
                                status
                            );
                        } else if (status == 429) {
                            log.warn("LLM provider rate limit reached (HTTP 429)");
                            throw new LlmRateLimitException(
                                "LLM provider rate limit exceeded (HTTP 429). Please retry later.",
                                status
                            );
                        } else {
                            log.warn("LLM provider rejected request with client error (HTTP {})", status);
                            throw new LlmBadRequestException(
                                "LLM provider rejected request (HTTP " + status + ").",
                                status
                            );
                        }
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        log.warn("LLM provider internal server error (HTTP {})", status);
                        throw new LlmServerException(
                            "LLM provider service error (HTTP " + status + "). Provider is temporarily unavailable.",
                            status
                        );
                    })
                    .body(ChatCompletionResponse.class);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new LlmServiceException("LLM provider returned empty response choices");
            }

            // Log response metadata only
            String finishReason = response.getChoices().get(0).getFinishReason();
            Integer promptTokens = response.getUsage() != null ? response.getUsage().getPromptTokens() : null;
            Integer completionTokens = response.getUsage() != null ? response.getUsage().getCompletionTokens() : null;

            log.info("LLM chat completion succeeded. Model: {}, Finish Reason: {}, Prompt Tokens: {}, Completion Tokens: {}",
                    response.getModel(), finishReason, promptTokens, completionTokens);

            return response;

        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("LLM provider request timed out ({})", ex.getClass().getSimpleName());
                throw new LlmTimeoutException("LLM provider request timed out. Please try again.", ex);
            }
            log.warn("LLM provider connection failed ({})", ex.getClass().getSimpleName());
            throw new LlmServiceException("Failed to connect to LLM provider.", ex);
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during LLM chat completion ({})", ex.getClass().getSimpleName());
            throw new LlmServiceException("Unexpected error during LLM communication.", ex);
        }
    }

    private boolean isTimeout(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String extractErrorMessage(String body, int status) {
        if (body == null || body.isBlank()) {
            return "HTTP " + status;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                JsonNode err = root.get("error");
                if (err.isObject() && err.has("message")) {
                    return err.get("message").asText();
                } else if (err.isTextual()) {
                    return err.asText();
                }
            }
        } catch (Exception ignored) {
            // fallback if not standard JSON error structure
        }
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
