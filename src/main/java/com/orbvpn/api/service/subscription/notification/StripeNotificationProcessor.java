package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.domain.dto.StripeWebhookEvent;
import com.orbvpn.api.service.webhook.WebhookEventCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeNotificationProcessor {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final RadiusService radiusService;
    private final WebhookService webhookService;
    private final WebhookEventCreator webhookEventCreator;

    public void processNotification(StripeWebhookEvent event) {
        log.info("Processing Stripe notification: {}", event.getType());

        try {
            switch (event.getType()) {
                case "customer.subscription.created" -> handleSubscriptionCreated(event);
                case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
                case "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(event);
                case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
                default -> log.info("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error processing Stripe webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process webhook", e);
        }
    }

    private void handleSubscriptionCreated(StripeWebhookEvent event) {
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionId(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();
            subscription.setExpiresAt(event.getExpiresAt());
            subscription.setCanceled(false);

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

            webhookService.processWebhook("SUBSCRIPTION_CREATED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        }
    }

    private void handleSubscriptionUpdated(StripeWebhookEvent event) {
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionId(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();
            subscription.setExpiresAt(event.getExpiresAt());

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

            webhookService.processWebhook("SUBSCRIPTION_UPDATED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        }
    }

    private void handleSubscriptionDeleted(StripeWebhookEvent event) {
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionId(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();
            subscription.setCanceled(true);

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

            webhookService.processWebhook("SUBSCRIPTION_CANCELLED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        }
    }

    private void handleInvoicePaymentSucceeded(StripeWebhookEvent event) {
        if (event.getSubscriptionId() != null) {
            Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                    .findByStripeSubscriptionId(event.getSubscriptionId());

            if (subscriptionOpt.isPresent()) {
                UserSubscription subscription = subscriptionOpt.get();
                subscription.setExpiresAt(event.getExpiresAt());
                subscription.setCanceled(false);

                userSubscriptionRepository.save(subscription);
                radiusService.updateUserExpirationRadCheck(subscription);

                webhookService.processWebhook("PAYMENT_SUCCEEDED",
                        webhookEventCreator.createSubscriptionPayload(subscription));
            }
        }
    }

    private void handleInvoicePaymentFailed(StripeWebhookEvent event) {
        if (event.getSubscriptionId() != null) {
            Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                    .findByStripeSubscriptionId(event.getSubscriptionId());

            if (subscriptionOpt.isPresent()) {
                UserSubscription subscription = subscriptionOpt.get();
                subscription.setStatus(SubscriptionStatus.PAYMENT_FAILED);

                userSubscriptionRepository.save(subscription);

                webhookService.processWebhook("PAYMENT_FAILED",
                        webhookEventCreator.createSubscriptionPayload(subscription));

                log.warn("Payment failed for subscription: {}", subscription.getId());
            }
        }
    }
}
