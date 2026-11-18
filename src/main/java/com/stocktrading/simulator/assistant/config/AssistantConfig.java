package com.stocktrading.simulator.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocktrading.simulator.assistant.client.LlmClient;
import com.stocktrading.simulator.assistant.client.UniversalLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration class for the StockSim AI Trading Assistant.
 * Registers {@link AssistantProperties} and instantiates {@link LlmClient}.
 * When {@code stocksim.assistant.enabled=false}, a dormant client is instantiated to allow
 * application startup without external connectivity or API credentials.
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {

    private static final Logger log = LoggerFactory.getLogger(AssistantConfig.class);

    @Bean
    public LlmClient llmClient(AssistantProperties properties,
                              ObjectMapper objectMapper,
                              RestClient.Builder restClientBuilder) {
        if (!properties.isEnabled()) {
            log.info("StockSim AI Assistant is disabled. Initializing dormant LlmClient bean.");
            return new UniversalLlmClient(properties, null, objectMapper);
        }

        properties.validate();
        log.info("Initializing StockSim AI Assistant LlmClient for model '{}' at endpoint '{}'",
                properties.getModel(), properties.getBaseUrl());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        if (properties.getConnectTimeout() != null) {
            requestFactory.setConnectTimeout(properties.getConnectTimeout());
        }
        if (properties.getReadTimeout() != null) {
            requestFactory.setReadTimeout(properties.getReadTimeout());
        }

        String baseUrl = properties.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        RestClient.Builder builder = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory);

        return new UniversalLlmClient(properties, builder.build(), objectMapper);
    }
}
