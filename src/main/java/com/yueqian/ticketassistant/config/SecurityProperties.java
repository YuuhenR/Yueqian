package com.yueqian.ticketassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ticket.assistant.security")
public class SecurityProperties {

    private String apiKey;
    private int maxRequestsPerMinute = 90;
    private String jwtSecret = "rail-ticket-assistant-demo-secret-change-me-please-2026";
    private long jwtExpireMinutes = 720;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getMaxRequestsPerMinute() {
        return maxRequestsPerMinute;
    }

    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpireMinutes() {
        return jwtExpireMinutes;
    }

    public void setJwtExpireMinutes(long jwtExpireMinutes) {
        this.jwtExpireMinutes = jwtExpireMinutes;
    }
}
