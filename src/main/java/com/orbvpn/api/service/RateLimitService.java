package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.RateLimit;
import com.orbvpn.api.repository.RateLimitRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class RateLimitService {

    private static final int MAX_REQUESTS = 3000000;
    private static final int COOLDOWN_PERIOD_MINUTES = 5;
    private static final int REFILL_INTERVAL_HOURS = 24;

    @Autowired
    private RateLimitRepository rateLimitRepository;

    public RateLimit getOrCreateRateLimit(String email) {
        return rateLimitRepository.findByEmail(email)
                .orElseGet(() -> {
                    RateLimit newRateLimit = new RateLimit();
                    newRateLimit.setEmail(email);
                    newRateLimit.setTokens(MAX_REQUESTS);
                    newRateLimit.setLastRefillTimestamp(LocalDateTime.now());
                    return rateLimitRepository.save(newRateLimit);
                });
    }

    public boolean canProceedWithRequest(RateLimit rateLimit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRefillTime = rateLimit.getLastRefillTimestamp().plusHours(REFILL_INTERVAL_HOURS);

        // Check if we should refill the tokens
        if (now.isAfter(nextRefillTime)) {
            rateLimit.setTokens(MAX_REQUESTS);
            rateLimit.setLastRefillTimestamp(now);
            rateLimitRepository.save(rateLimit);
        } else if (rateLimit.getTokens() <= 0) {
            // If not time for a refill, and tokens are exhausted, check if we're still
            // within the cooldown period
            LocalDateTime endOfCooldown = rateLimit.getLastRefillTimestamp().plusMinutes(COOLDOWN_PERIOD_MINUTES);
            if (now.isBefore(endOfCooldown)) {
                return false; // Still in cooldown period
            }

            // If cooldown period is over, grant one token
            rateLimit.setTokens(1);
            rateLimitRepository.save(rateLimit);
        } else {
            // Deduct a token and proceed
            rateLimit.setTokens(rateLimit.getTokens() - 1);
            rateLimitRepository.save(rateLimit);
        }

        return true;
    }
}
