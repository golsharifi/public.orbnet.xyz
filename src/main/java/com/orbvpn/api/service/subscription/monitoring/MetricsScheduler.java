package com.orbvpn.api.service.subscription.monitoring;

import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class MetricsScheduler {
    private final UserSubscriptionRepository subscriptionRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void updateMetrics() {
        try {
            // Basic subscription counts
            long activeCount = subscriptionRepository.countActiveSubscriptions();
            long expiredCount = subscriptionRepository.countExpiredSubscriptions();
            long trialCount = subscriptionRepository.countTrialSubscriptions();

            // Register metrics
            meterRegistry.gauge("subscription.active.count", activeCount);
            meterRegistry.gauge("subscription.expired.count", expiredCount);
            meterRegistry.gauge("subscription.trial.count", trialCount);

            // Additional metrics
            long autoRenewCount = subscriptionRepository.countAutoRenewSubscriptions();
            meterRegistry.gauge("subscription.auto_renew.count", autoRenewCount);

            // Expiring soon metrics
            LocalDateTime now = LocalDateTime.now();
            long expiringIn24h = subscriptionRepository.countSubscriptionsExpiringBetween(
                    now, now.plusHours(24));
            long expiringIn7d = subscriptionRepository.countSubscriptionsExpiringBetween(
                    now, now.plusDays(7));

            meterRegistry.gauge("subscription.expiring.24h", expiringIn24h);
            meterRegistry.gauge("subscription.expiring.7d", expiringIn7d);

            // Gateway-specific metrics using enum
            updateGatewayMetrics();

        } catch (Exception e) {
            log.error("Failed to update subscription metrics", e);
            // Record metric update failure
            meterRegistry.counter("subscription.metrics.update.failures").increment();
        }
    }

    private void updateGatewayMetrics() {
        try {
            for (GatewayName gateway : GatewayName.values()) {
                // Existing count metrics
                long count = subscriptionRepository.countActiveSubscriptionsByGateway(gateway);
                meterRegistry.gauge("subscription.gateway.count",
                        Tags.of("gateway", gateway.name().toLowerCase()),
                        count);

                // Add price metrics
                long withPriceCount = subscriptionRepository
                        .countActiveSubscriptionsWithPrice(gateway);
                BigDecimal avgPrice = subscriptionRepository
                        .getAveragePriceByGateway(gateway);

                meterRegistry.gauge("subscription.gateway.with_price.count",
                        Tags.of("gateway", gateway.name().toLowerCase()),
                        withPriceCount);

                if (avgPrice != null) {
                    meterRegistry.gauge("subscription.gateway.average_price",
                            Tags.of("gateway", gateway.name().toLowerCase()),
                            avgPrice.doubleValue());
                }
            }

            // Log summary with price information
            log.info("""
                    Subscription metrics by gateway:
                    - Google Play: {} (with price: {}, avg: {})
                    - Apple Store: {} (with price: {}, avg: {})
                    - Stripe: {} (with price: {}, avg: {})
                    - PayPal: {} (with price: {}, avg: {})
                    - Other: {}
                    """,
                    subscriptionRepository.countActiveSubscriptionsByGateway(GatewayName.GOOGLE_PLAY),
                    subscriptionRepository.countActiveSubscriptionsWithPrice(GatewayName.GOOGLE_PLAY),
                    subscriptionRepository.getAveragePriceByGateway(GatewayName.GOOGLE_PLAY),
                    subscriptionRepository.countActiveSubscriptionsByGateway(GatewayName.APPLE_STORE),
                    subscriptionRepository.countActiveSubscriptionsWithPrice(GatewayName.APPLE_STORE),
                    subscriptionRepository.getAveragePriceByGateway(GatewayName.APPLE_STORE),
                    subscriptionRepository.countActiveSubscriptionsByGateway(GatewayName.STRIPE),
                    subscriptionRepository.countActiveSubscriptionsWithPrice(GatewayName.STRIPE),
                    subscriptionRepository.getAveragePriceByGateway(GatewayName.STRIPE),
                    subscriptionRepository.countActiveSubscriptionsByGateway(GatewayName.PAYPAL),
                    subscriptionRepository.countActiveSubscriptionsWithPrice(GatewayName.PAYPAL),
                    subscriptionRepository.getAveragePriceByGateway(GatewayName.PAYPAL),
                    countOtherGateways());
        } catch (Exception e) {
            log.error("Failed to update gateway metrics", e);
        }
    }

    private long countOtherGateways() {
        return Arrays.stream(GatewayName.values())
                .filter(gateway -> !gateway.equals(GatewayName.GOOGLE_PLAY)
                        && !gateway.equals(GatewayName.APPLE_STORE)
                        && !gateway.equals(GatewayName.STRIPE)
                        && !gateway.equals(GatewayName.PAYPAL))
                .mapToLong(gateway -> subscriptionRepository.countActiveSubscriptionsByGateway(gateway))
                .sum();
    }
}
