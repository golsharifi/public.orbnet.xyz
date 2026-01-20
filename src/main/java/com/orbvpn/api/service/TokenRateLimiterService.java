package com.orbvpn.api.service;

import com.orbvpn.api.config.RateLimitProperties;
import io.github.bucket4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Service
public class TokenRateLimiterService {
    private final Map<String, Bucket> tokenBuckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccessTime = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    private final BucketConfiguration resellerConfig;
    private final BucketConfiguration userConfig;

    public TokenRateLimiterService(RateLimitProperties properties) {
        this.properties = properties;

        // Initialize reseller configuration with intervally refill
        this.resellerConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(
                        properties.getTokenResellerLimit(),
                        Duration.ofMinutes(1)))
                .build();

        // Initialize user configuration with greedy refill
        this.userConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(
                        properties.getTokenUserLimit(),
                        Refill.greedy(properties.getTokenUserLimit(), Duration.ofDays(1))))
                .build();
    }

    public boolean isAllowed(String token, String roleName) {
        if (ADMIN.equals(roleName)) {
            return true;
        }

        try {
            Bucket bucket = tokenBuckets.computeIfAbsent(token, k -> {
                lastAccessTime.put(token, System.currentTimeMillis());
                if (RESELLER.equals(roleName)) {
                    return Bucket.builder()
                            .addLimit(resellerConfig.getBandwidths()[0])
                            .build();
                } else if (USER.equals(roleName)) {
                    return Bucket.builder()
                            .addLimit(userConfig.getBandwidths()[0])
                            .build();
                } else {
                    throw new IllegalArgumentException("Invalid role name provided");
                }
            });

            lastAccessTime.put(token, System.currentTimeMillis());
            return bucket.tryConsume(1);
        } catch (Exception e) {
            log.error("Error in token rate limiting for token {}: {}", token, e.getMessage());
            return false;
        }
    }

    @Scheduled(fixedRateString = "${ratelimit.cleanup-interval:1800000}")
    public void cleanupOldBuckets() {
        long cutoffTime = System.currentTimeMillis() - properties.getCleanupInterval().toMillis();

        lastAccessTime.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoffTime) {
                tokenBuckets.remove(entry.getKey());
                lastAccessTime.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}