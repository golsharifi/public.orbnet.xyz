package com.orbvpn.api.service.notification;

import com.orbvpn.api.config.RateLimitProperties;
import io.github.bucket4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MessageRateLimiter {
    private final Map<String, Bucket> whatsappBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> telegramBuckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    private final BucketConfiguration whatsappConfig;
    private final BucketConfiguration telegramConfig;
    private final RateLimitProperties properties;

    public MessageRateLimiter(RateLimitProperties properties) {
        this.properties = properties;

        // Initialize WhatsApp configuration with hourly and minute-based refill
        this.whatsappConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(
                        properties.getWhatsapp().getTokensPerHour(),
                        Duration.ofHours(1)))
                .addLimit(Bandwidth.simple(
                        properties.getWhatsapp().getTokensPerMinute(),
                        Duration.ofMinutes(1)))
                .build();

        // Initialize Telegram configuration with hourly and minute-based refill
        this.telegramConfig = BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(
                        properties.getTelegram().getTokensPerHour(),
                        Duration.ofHours(1)))
                .addLimit(Bandwidth.simple(
                        properties.getTelegram().getTokensPerMinute(),
                        Duration.ofMinutes(1)))
                .build();
    }

    public boolean tryConsumeWhatsApp(String phoneNumber) {
        try {
            Bucket bucket = whatsappBuckets.computeIfAbsent(phoneNumber, k -> {
                lastAccessTime.put(phoneNumber, System.currentTimeMillis());
                return Bucket.builder()
                        .addLimit(whatsappConfig.getBandwidths()[0])
                        .addLimit(whatsappConfig.getBandwidths()[1])
                        .build();
            });

            lastAccessTime.put(phoneNumber, System.currentTimeMillis());
            boolean consumed = bucket.tryConsume(1);

            if (!consumed) {
                log.warn("Rate limit exceeded for WhatsApp number: {}", phoneNumber);
            }
            return consumed;
        } catch (Exception e) {
            log.error("Error in WhatsApp rate limiting for number {}: {}", phoneNumber, e.getMessage());
            return false;
        }
    }

    public boolean tryConsumeTelegram(String chatId) {
        try {
            Bucket bucket = telegramBuckets.computeIfAbsent(chatId, k -> {
                lastAccessTime.put(chatId, System.currentTimeMillis());
                return Bucket.builder()
                        .addLimit(telegramConfig.getBandwidths()[0])
                        .addLimit(telegramConfig.getBandwidths()[1])
                        .build();
            });

            lastAccessTime.put(chatId, System.currentTimeMillis());
            boolean consumed = bucket.tryConsume(1);

            if (!consumed) {
                log.warn("Rate limit exceeded for Telegram chat: {}", chatId);
            }
            return consumed;
        } catch (Exception e) {
            log.error("Error in Telegram rate limiting for chat {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    public int getRemainingWhatsAppTokens(String phoneNumber) {
        Bucket bucket = whatsappBuckets.get(phoneNumber);
        if (bucket == null) {
            return properties.getWhatsapp().getTokensPerHour();
        }
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(0);
        return (int) probe.getRemainingTokens();
    }

    public int getRemainingTelegramTokens(String chatId) {
        Bucket bucket = telegramBuckets.get(chatId);
        if (bucket == null) {
            return properties.getTelegram().getTokensPerHour();
        }
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(0);
        return (int) probe.getRemainingTokens();
    }

    @Scheduled(fixedRateString = "${ratelimit.cleanup-interval:1800000}")
    public void cleanupOldBuckets() {
        long cutoffTime = System.currentTimeMillis() - properties.getCleanupInterval().toMillis();

        lastAccessTime.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoffTime) {
                String key = entry.getKey();
                whatsappBuckets.remove(key);
                telegramBuckets.remove(key);
                lastAccessTime.remove(key);
                return true;
            }
            return false;
        });
    }
}