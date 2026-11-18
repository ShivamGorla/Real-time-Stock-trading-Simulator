package com.stocktrading.simulator.assistant.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Abstract base class providing strict JSON schema argument deserialization and result serialization.
 * Enforces {@code additionalProperties: false} at the Jackson layer via {@code FAIL_ON_UNKNOWN_PROPERTIES}.
 */
public abstract class AbstractAssistantTool implements AssistantTool {

    protected final ObjectMapper objectMapper;

    protected AbstractAssistantTool(ObjectMapper objectMapper) {
        if (objectMapper != null) {
            this.objectMapper = objectMapper.copy()
                    .registerModule(new JavaTimeModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        } else {
            this.objectMapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        }
    }

    protected <T> T parseArguments(String argumentsJson, Class<T> clazz, boolean required) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            if (required) {
                throw new IllegalArgumentException("Missing required arguments for tool '" + getName() + "'");
            }
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Cannot instantiate empty arguments for " + clazz.getSimpleName(), ex);
            }
        }
        try {
            return objectMapper.readValue(argumentsJson, clazz);
        } catch (UnrecognizedPropertyException ex) {
            throw new IllegalArgumentException(
                "Unrecognized argument property '" + ex.getPropertyName() + "' for tool '" + getName() + "'. Unknown parameters are not permitted."
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Malformed JSON arguments for tool '" + getName() + "'.");
        }
    }

    protected String serializeResult(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize tool result for '" + getName() + "'.", ex);
        }
    }
}
