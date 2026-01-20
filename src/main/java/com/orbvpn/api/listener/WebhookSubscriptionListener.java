package com.orbvpn.api.listener;

import com.orbvpn.api.domain.enums.WebhookEventType;
import com.orbvpn.api.event.SubscriptionChangedEvent;
import com.orbvpn.api.service.webhook.WebhookEventProvider;
import com.orbvpn.api.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSubscriptionListener {
    private final WebhookService webhookService;
    private final WebhookEventProvider webhookEventProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubscriptionChanged(SubscriptionChangedEvent event) {
        try {
            log.debug("Processing webhook for subscription event: {}", event.getEventType());

            WebhookEventType webhookEventType = mapEventType(event.getEventType());
            webhookService.processWebhook(
                    webhookEventType.getEventName(),
                    webhookEventProvider.createSubscriptionPayload(event.getSubscription()));
        } catch (Exception e) {
            log.error("Error processing webhook for subscription event: {}", event.getEventType(), e);
        }
    }

    private WebhookEventType mapEventType(String eventType) {
        return switch (eventType) {
            case "NEW_SUBSCRIPTION" -> WebhookEventType.SUBSCRIPTION_CREATED;
            case "SUBSCRIPTION_RENEWED" -> WebhookEventType.SUBSCRIPTION_RENEWED;
            case "SUBSCRIPTION_EXPIRED" -> WebhookEventType.SUBSCRIPTION_EXPIRED;
            case "SUBSCRIPTION_CANCELLED" -> WebhookEventType.SUBSCRIPTION_CANCELLED;
            case "SUBSCRIPTION_UPDATED" -> WebhookEventType.SUBSCRIPTION_UPDATED;
            case "TRIAL_STARTED" -> WebhookEventType.SUBSCRIPTION_TRIAL_STARTED;
            case "TRIAL_ENDED" -> WebhookEventType.SUBSCRIPTION_TRIAL_ENDED;
            case "AUTO_RENEW_ENABLED" -> WebhookEventType.SUBSCRIPTION_AUTO_RENEW_ENABLED;
            case "AUTO_RENEW_DISABLED" -> WebhookEventType.SUBSCRIPTION_AUTO_RENEW_DISABLED;
            case "SUBSCRIPTION_PLAN_CHANGED" -> WebhookEventType.SUBSCRIPTION_PLAN_CHANGED;
            case "SUBSCRIPTION_REMOVED" -> WebhookEventType.SUBSCRIPTION_REMOVED;
            case "SUBSCRIPTION_REVERTED" -> WebhookEventType.SUBSCRIPTION_REVERTED;
            default -> {
                log.warn("Unmapped subscription event type: {}", eventType);
                yield WebhookEventType.SUBSCRIPTION_UPDATED; // Default fallback
            }
        };
    }
}
