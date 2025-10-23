package com.orbvpn.api.service.subscription.handlers;

import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.entity.User;

import java.time.LocalDateTime;

public interface SubscriptionHandler {
    void handleSubscription(User user, int groupId, LocalDateTime expiresAt,
            String token, Boolean isTrialPeriod, String subscriptionId);

    GatewayName getGatewayType();
}