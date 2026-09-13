package com.freshmart.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class DeepSeekClient {
    private final RestClient client;
    private final String apiKey;
    private final String model;

    public DeepSeekClient(@Value("${ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${ai.deepseek.api-key:}") String apiKey,
            @Value("${ai.deepseek.model:deepseek-chat}") String model,
            @Value("${ai.deepseek.timeout-seconds:15}") long timeoutSeconds) {
        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    public String chat(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "DeepSeek API is not configured");
        }
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0.2,
                "max_tokens", 500,
                "messages", new Object[] {
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                });
        try {
            JsonNode response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? null : response.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "DeepSeek returned an empty response");
            }
            return content.trim();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "DeepSeek API request failed");
        }
    }
}
