package com.orbvpn.api.service.subscription.utils;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.repository.UsedTransactionIdRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.exception.SubscriptionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidator {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UsedTransactionIdRepository usedTransactionIdRepository;

    @Transactional(readOnly = true)
    public void validateToken(String token, GatewayName gateway, User user) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        // First check if this token belongs to this user
        UserSubscription existingSubscription = findExistingSubscription(token, gateway);
        if (existingSubscription != null) {
            if (existingSubscription.getUser().getId() != user.getId()) {
                log.warn("Token {} belongs to a different user", token);
                throw new SubscriptionException("Token belongs to a different user");
            }
            return; // Token is valid for this user
        }

        // Then check if token is already used by someone else
        if (isTokenUsed(token, gateway)) {
            log.warn("Token {} has already been used", token);
            throw new SubscriptionException("Token has already been used");
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