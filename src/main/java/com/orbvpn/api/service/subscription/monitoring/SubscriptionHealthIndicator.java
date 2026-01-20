package com.orbvpn.api.service.subscription.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("subscriptionHealth")
public class SubscriptionHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // Simple implementation that always reports UP
        return Health.up()
                .withDetail("status", "OK")
                .build();
    }
}