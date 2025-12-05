package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookHelper {
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;
    private final MeterRegistry meterRegistry;

    /**
     * Send webhook asynchronously to avoid blocking the main request thread
     */
    @Async
    public void sendWebhookWithRetry(String eventType, UserSubscription subscription) {
        try {
            webhookService.processWebhook(eventType,
                    webhookEventCreator.createSubscriptionPayload(subscription));
            recordMetric(eventType, true);
            log.debug("Async webhook sent: {} for subscription {}", eventType, subscription.getId());
        } catch (Exception e) {
            log.error("Failed to send webhook for event {}: {}", eventType, e.getMessage());
            recordMetric(eventType, false);
        }
    }

    private void recordMetric(String eventType, boolean success) {
        if (meterRegistry != null) {
            meterRegistry.counter("webhook.delivery",
                    "event_type", eventType,
                    "success", String.valueOf(success)).increment();
        }
    }
}
