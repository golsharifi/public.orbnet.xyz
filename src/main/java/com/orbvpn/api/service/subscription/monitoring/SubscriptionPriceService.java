package com.orbvpn.api.service.subscription.monitoring;

import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionPriceService {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void saveSubscriptionWithPrice(UserSubscription subscription,
            BigDecimal price, String currency) {
        try {
            subscription.setPrice(price);
            subscription.setCurrency(currency);
            UserSubscription saved = userSubscriptionRepository.saveAndFlush(subscription);

            log.info("Saved subscription {} with price: {} {}",
                    saved.getId(), saved.getPrice(), saved.getCurrency());

            // Record metric for successful price update
            meterRegistry.counter("subscription.price.updates",
                    Tags.of("gateway", subscription.getGateway().name().toLowerCase()))
                    .increment();

        } catch (Exception e) {
            log.error("Error saving subscription with price: {}", e.getMessage(), e);

            // Record metric for failed price update
            meterRegistry.counter("subscription.price.update.failures",
                    Tags.of("gateway", subscription.getGateway().name().toLowerCase()))
                    .increment();

            throw e;
        }
    }

    @Transactional(readOnly = true)
    public void verifyPriceInformation(UserSubscription subscription) {
        try {
            if (subscription.getPrice() == null || subscription.getCurrency() == null) {
                log.warn("Missing price information for subscription: {} - Gateway: {}",
                        subscription.getId(), subscription.getGateway());

                meterRegistry.counter("subscription.price.missing",
                        Tags.of("gateway", subscription.getGateway().name().toLowerCase()))
                        .increment();
            }
        } catch (Exception e) {
            log.error("Error verifying price information: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void updatePriceInformation(UserSubscription subscription,
            BigDecimal newPrice, String newCurrency) {
        try {
            // Record old price for logging
            BigDecimal oldPrice = subscription.getPrice();
            String oldCurrency = subscription.getCurrency();

            subscription.setPrice(newPrice);
            subscription.setCurrency(newCurrency);
            UserSubscription saved = userSubscriptionRepository.saveAndFlush(subscription);

            log.info("Updated subscription {} price from {} {} to {} {}",
                    saved.getId(), oldPrice, oldCurrency, newPrice, newCurrency);

            // Record metric for price change
            meterRegistry.counter("subscription.price.changes",
                    Tags.of("gateway", subscription.getGateway().name().toLowerCase()))
                    .increment();

        } catch (Exception e) {
            log.error("Error updating price information: {}", e.getMessage(), e);
            throw e;
        }
    }
}
