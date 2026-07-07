package com.yueqian.ticketassistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;

    public DeepSeekClient(ObjectMapper objectMapper,
                          @Value("${ticket.assistant.ai.deepseek-api-key:}") String apiKey,
                          @Value("${ticket.assistant.ai.deepseek-base-url:https://api.deepseek.com}") String baseUrl,
                          @Value("${ticket.assistant.ai.model-name:deepseek-chat}") String modelName) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.modelName = modelName;
    }

    public String chat(List<Message> messages) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", modelName,
                    "temperature", 0.3,
                    "messages", messages.stream()
                            .map(item -> Map.of("role", item.role(), "content", item.content()))
                            .toList()
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    public record Message(String role, String content) {
    }
}
