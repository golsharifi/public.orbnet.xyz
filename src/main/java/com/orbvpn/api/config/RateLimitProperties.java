package com.orbvpn.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {
    // API Rate Limits
    private int ipLimit = 500;
    private Duration ipRefillDuration = Duration.ofHours(1);
    private int resellerLimit = 500;
    private Duration resellerRefillDuration = Duration.ofMinutes(1);
    private int userLimit = 1500;
    private Duration userRefillDuration = Duration.ofDays(1);

    // Token Service Limits
    private int tokenResellerLimit = 100;
    private int tokenUserLimit = 300;

    // Messaging Rate Limits
    private MessagingRateLimit whatsapp = new MessagingRateLimit();
    private MessagingRateLimit telegram = new MessagingRateLimit();

    // General Settings
    private Duration cleanupInterval = Duration.ofMinutes(30);
    private int blacklistThreshold = 5;

    @Data
    public static class MessagingRateLimit {
        private int tokensPerHour = 100;
        private int tokensPerMinute = 10;
    }
}