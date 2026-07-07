package com.yueqian.ticketassistant.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

@Component
public class WeatherTool {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiHost;
    private final String geoBaseUrl;
    private final String weatherBaseUrl;

    public WeatherTool(ObjectMapper objectMapper,
                       @Value("${ticket.assistant.weather.qweather-api-key:}") String apiKey,
                       @Value("${ticket.assistant.weather.qweather-api-host:}") String apiHost,
                       @Value("${ticket.assistant.weather.geo-base-url:https://geoapi.qweather.com}") String geoBaseUrl,
                       @Value("${ticket.assistant.weather.weather-base-url:https://devapi.qweather.com}") String weatherBaseUrl) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiHost = normalizeHost(apiHost);
        this.geoBaseUrl = trimEnd(geoBaseUrl);
        this.weatherBaseUrl = trimEnd(weatherBaseUrl);
    }

    public String query(String city) {
        String safeCity = StringUtils.hasText(city) ? city.trim() : "\u5317\u4eac";
        if (!StringUtils.hasText(apiKey)) {
            return safeCity + "\u5929\u6c14\u670d\u52a1\u6682\u4e0d\u53ef\u7528";
        }
        try {
            String locationId = lookupLocationId(safeCity);
            if (!StringUtils.hasText(locationId)) {
                return "\u672a\u67e5\u8be2\u5230" + safeCity + "\u7684\u5929\u6c14\u6570\u636e";
            }
            String url = weatherBase() + "/v7/weather/now?location=" + encode(locationId)
                    + "&key=" + encode(apiKey) + "&lang=zh&unit=m";
            JsonNode root = get(url);
            if (!"200".equals(root.path("code").asText())) {
                return safeCity + failure(root);
            }
            JsonNode now = root.path("now");
            return safeCity + "\u5f53\u524d" + now.path("text").asText()
                    + "\uff0c" + now.path("temp").asText() + "\u2103"
                    + "\uff0c\u4f53\u611f" + now.path("feelsLike").asText() + "\u2103"
                    + "\uff0c" + now.path("windDir").asText() + now.path("windScale").asText() + "\u7ea7"
                    + "\uff0c\u6e7f\u5ea6" + now.path("humidity").asText() + "%\u3002";
        } catch (Exception ex) {
            return safeCity + "\u5929\u6c14\u67e5\u8be2\u5931\u8d25";
        }
    }

    private String lookupLocationId(String city) throws Exception {
        String url = geoBase() + "/v2/city/lookup?location=" + encode(city)
                + "&key=" + encode(apiKey) + "&range=cn&number=1&lang=zh";
        JsonNode root = get(url);
        if (!"200".equals(root.path("code").asText())) {
            return "";
        }
        return root.path("location").path(0).path("id").asText("");
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("X-QW-Api-Key", apiKey)
                .header("Accept-Encoding", "gzip")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String body = new String(decodeBody(response), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return objectMapper.createObjectNode().put("code", String.valueOf(response.statusCode()));
        }
        return objectMapper.readTree(body);
    }

    private byte[] decodeBody(HttpResponse<byte[]> response) throws Exception {
        String encoding = response.headers().firstValue("content-encoding").orElse("");
        if (!"gzip".equalsIgnoreCase(encoding)) {
            return response.body();
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(response.body()));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gzip.transferTo(out);
            return out.toByteArray();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimEnd(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizeHost(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String host = trimEnd(value.trim());
        return host.startsWith("http://") || host.startsWith("https://") ? host : "https://" + host;
    }

    private String geoBase() {
        return StringUtils.hasText(apiHost) ? apiHost + "/geo" : geoBaseUrl;
    }

    private String weatherBase() {
        return StringUtils.hasText(apiHost) ? apiHost : weatherBaseUrl;
    }

    private String failure(JsonNode root) {
        JsonNode error = root.path("error");
        String title = error.path("title").asText("");
        if ("Invalid Host".equalsIgnoreCase(title)) {
            return "\u5929\u6c14\u67e5\u8be2\u5931\u8d25\uff1a\u8bf7\u68c0\u67e5 QWEATHER_API_HOST";
        }
        String code = root.path("code").asText("");
        return code.isBlank() ? "\u5929\u6c14\u67e5\u8be2\u5931\u8d25" : "\u5929\u6c14\u67e5\u8be2\u5931\u8d25\uff1a" + code;
    }
}
