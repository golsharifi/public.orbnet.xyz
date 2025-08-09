package com.orbvpn.api.filter;

import com.orbvpn.api.config.RateLimitProperties;
import com.orbvpn.api.service.IPService;
import io.github.bucket4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
public class UserRateLimiter {
    private final IPService ipService;
    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<String, Integer> ipViolations = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    private final BucketConfiguration ipConfig;
    private final BucketConfiguration resellerConfig;
    private final BucketConfiguration userConfig;
    private final RateLimitProperties properties;

    public UserRateLimiter(IPService ipService, RateLimitProperties properties) {
        this.ipService = ipService;
        this.properties = properties;

        // Initialize IP rate limit configuration
        this.ipConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(properties.getIpLimit(), properties.getIpRefillDuration()))
                .build();

        // Initialize reseller rate limit configuration
        this.resellerConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(properties.getResellerLimit(), properties.getResellerRefillDuration()))
                .build();

        // Initialize user rate limit configuration
        this.userConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(properties.getUserLimit(),
                        Refill.greedy(properties.getUserLimit(), properties.getUserRefillDuration())))
                .build();
    }

    public boolean isAllowedForIp(String ip) {
        try {
            Bucket bucket = ipBuckets.computeIfAbsent(ip, k -> {
                lastAccessTime.put(ip, System.currentTimeMillis());
                return Bucket.builder()
                        .addLimit(ipConfig.getBandwidths()[0])
                        .build();
            });

            lastAccessTime.put(ip, System.currentTimeMillis());
            boolean allowed = bucket.tryConsume(1);

            if (!allowed) {
                handleViolation(ip);
            } else {
                ipViolations.remove(ip);
            }

            return allowed;
        } catch (Exception e) {
            log.error("Error in rate limiting for IP {}: {}", ip, e.getMessage());
            return false;
        }
    }

    public boolean isAllowedForUser(String userId, String roleName) {
        if (ADMIN.equals(roleName)) {
            return true;
        }

        try {
            Bucket bucket = userBuckets.computeIfAbsent(userId, k -> {
                lastAccessTime.put(userId, System.currentTimeMillis());
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

            lastAccessTime.put(userId, System.currentTimeMillis());
            return bucket.tryConsume(1);
        } catch (Exception e) {
            log.error("Error in rate limiting for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private void handleViolation(String ip) {
        int violations = ipViolations.compute(ip, (k, v) -> v == null ? 1 : v + 1);
        if (violations >= properties.getBlacklistThreshold()) {
            log.warn("IP {} exceeded violation threshold. Adding to blacklist.", ip);
            ipService.addToBlacklist(ip);
            cleanup(ip);
        }
    }

    @Scheduled(fixedRateString = "${ratelimit.cleanup-interval:1800000}")
    public void cleanupOldBuckets() {
        long cutoffTime = System.currentTimeMillis() - properties.getCleanupInterval().toMillis();

        lastAccessTime.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoffTime) {
                cleanup(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void cleanup(String key) {
        ipBuckets.remove(key);
        userBuckets.remove(key);
        ipViolations.remove(key);
        lastAccessTime.remove(key);
    }

    public ConsumptionProbe getProbe(String key, int tokens) {
        Bucket bucket = ipBuckets.get(key);
        return bucket != null ? bucket.tryConsumeAndReturnRemaining(tokens) : null;
    }
}