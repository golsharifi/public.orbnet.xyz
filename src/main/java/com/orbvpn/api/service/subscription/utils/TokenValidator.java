package com.orbvpn.api.service.subscription.utils;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.entity.UsedTransactionId;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.repository.UsedTransactionIdRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.exception.SubscriptionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidator {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UsedTransactionIdRepository usedTransactionIdRepository;

    /**
     * Validates that a purchase token has not been used before.
     * SECURITY: Rejects ALL token reuse, even for the same user.
     * Uses SERIALIZABLE isolation to prevent race conditions.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void validateToken(String token, GatewayName gateway, User user) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        log.info("Validating token for user {} on gateway {}", user.getId(), gateway);

        // Check 1: Is this token already recorded as used?
        if (isTokenUsed(token, gateway)) {
            log.warn("SECURITY: Token {} has already been used (UsedTransactionId table)", token);
            throw new SubscriptionException("This purchase token has already been processed");
        }

        // Check 2: Does an active subscription already exist with this token?
        UserSubscription existingSubscription = findExistingSubscription(token, gateway);
        if (existingSubscription != null) {
            // Token is already associated with a subscription - reject reuse
            if (existingSubscription.getUser().getId() == user.getId()) {
                log.warn("SECURITY: Token {} already used by same user {} - rejecting reuse",
                        token, user.getId());
                throw new SubscriptionException("This purchase has already been applied to your account");
            } else {
                log.warn("SECURITY: Token {} belongs to different user {} (requested by {})",
                        token, existingSubscription.getUser().getId(), user.getId());
                throw new SubscriptionException("This purchase token belongs to another account");
            }
        }

        log.info("Token validation passed for user {} - token is new", user.getId());
    }

    /**
     * Records a token as used after successful subscription assignment.
     * Should be called AFTER subscription is successfully created.
     */
    @Transactional
    public void markTokenAsUsed(String token, GatewayName gateway, User user) {
        try {
            // Double-check it's not already recorded (defensive)
            if (isTokenUsed(token, gateway)) {
                log.warn("Token {} already marked as used, skipping", token);
                return;
            }

            UsedTransactionId usedToken = new UsedTransactionId(token, gateway);
            usedTransactionIdRepository.save(usedToken);
            log.info("Marked token as used for user {} on gateway {}", user.getId(), gateway);
        } catch (Exception e) {
            // This is critical - if we can't mark the token, it could be reused
            log.error("CRITICAL: Failed to mark token as used: {} - {}", token, e.getMessage());
            throw new SubscriptionException("Failed to finalize purchase - please contact support", e);
        }
    }

    private UserSubscription findExistingSubscription(String token, GatewayName gateway) {
        if (gateway == GatewayName.APPLE_STORE) {
            return userSubscriptionRepository.findByOriginalTransactionId(token);
        } else if (gateway == GatewayName.GOOGLE_PLAY) {
            return userSubscriptionRepository.findByPurchaseToken(token);
        }
        return null;
    }

    private boolean isTokenUsed(String token, GatewayName gateway) {
        return usedTransactionIdRepository
                .findByTransactionIdAndGateway(token, gateway)
                .isPresent();
    }
}