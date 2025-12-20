package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.domain.dto.StripeWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeNotificationProcessor {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final RadiusService radiusService;
    private final AsyncNotificationHelper asyncNotificationHelper;

    @Transactional
    public void processNotification(StripeWebhookEvent event) {
        log.info("Processing Stripe notification: {} (eventId: {})", event.getType(), event.getEventId());

        try {
            switch (event.getType()) {
                case "customer.subscription.created" -> handleSubscriptionCreated(event);
                case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
                case "customer.subscription.trial_will_end" -> handleTrialWillEnd(event);
                case "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(event);
                case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
                case "charge.refunded" -> handleChargeRefunded(event);
                case "charge.dispute.created" -> handleDisputeCreated(event);
                default -> log.info("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Error processing Stripe webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process webhook", e);
        }
    }

    private void handleSubscriptionCreated(StripeWebhookEvent event) {
        // Use pessimistic locking to prevent race conditions
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionIdWithLock(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();
            subscription.setExpiresAt(event.getExpiresAt());
            subscription.setCanceled(false);
            subscription.setStatus(SubscriptionStatus.ACTIVE);

            // Track trial period
            if (Boolean.TRUE.equals(event.getIsTrialPeriod())) {
                subscription.setIsTrialPeriod(true);
                subscription.setTrialEndDate(event.getTrialEnd());
            }

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CREATED");

            log.info("Subscription created: {} for user {}", subscription.getId(), subscription.getUser().getId());
        } else {
            log.warn("No subscription found for Stripe subscription ID: {}", event.getSubscriptionId());
        }
    }

    private void handleSubscriptionUpdated(StripeWebhookEvent event) {
        // Use pessimistic locking to prevent race conditions
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionIdWithLock(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();

            // Update expiration
            if (event.getExpiresAt() != null) {
                subscription.setExpiresAt(event.getExpiresAt());
            }

            // Handle cancelAtPeriodEnd (soft cancel) vs immediate cancel
            if (Boolean.TRUE.equals(event.getCancelAtPeriodEnd())) {
                // Soft cancel - service continues until period end
                subscription.setAutoRenew(false);
                // Don't set canceled=true yet - still active until period end
                log.info("Subscription {} marked for cancellation at period end", subscription.getId());
            } else if ("canceled".equals(event.getStatus())) {
                // Immediate/hard cancel
                subscription.setCanceled(true);
                subscription.setAutoRenew(false);
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                subscription.setExpiresAt(LocalDateTime.now());
                log.info("Subscription {} immediately canceled", subscription.getId());
            } else if ("active".equals(event.getStatus())) {
                // Active subscription (possibly reactivated)
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setCanceled(false);
                // If cancelAtPeriodEnd was removed, restore auto-renew
                if (Boolean.FALSE.equals(event.getCancelAtPeriodEnd())) {
                    subscription.setAutoRenew(true);
                }
            } else if ("past_due".equals(event.getStatus())) {
                subscription.setStatus(SubscriptionStatus.PAYMENT_FAILED);
            } else if ("unpaid".equals(event.getStatus())) {
                subscription.setStatus(SubscriptionStatus.PAYMENT_FAILED);
            } else if ("trialing".equals(event.getStatus())) {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setIsTrialPeriod(true);
                subscription.setTrialEndDate(event.getTrialEnd());
            }

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_UPDATED");
        } else {
            log.warn("No subscription found for Stripe subscription ID: {}", event.getSubscriptionId());
        }
    }

    private void handleSubscriptionDeleted(StripeWebhookEvent event) {
        // Use pessimistic locking to prevent race conditions
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionIdWithLock(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();
            subscription.setCanceled(true);
            subscription.setAutoRenew(false);
            subscription.setStatus(SubscriptionStatus.EXPIRED);

            // Expire immediately on deletion
            if (subscription.getExpiresAt() == null || subscription.getExpiresAt().isAfter(LocalDateTime.now())) {
                subscription.setExpiresAt(LocalDateTime.now());
            }

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CANCELLED");

            log.info("Subscription {} deleted/cancelled", subscription.getId());
        } else {
            log.warn("No subscription found for Stripe subscription ID: {}", event.getSubscriptionId());
        }
    }

    private void handleTrialWillEnd(StripeWebhookEvent event) {
        // Trial is ending soon - send notification to user
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionIdWithLock(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();

            // Send trial ending notification asynchronously
            asyncNotificationHelper.sendTrialEndingNotificationAsync(
                    subscription.getUser(),
                    subscription,
                    event.getTrialEnd()
            );
            log.info("Queued trial ending notification for subscription {}", subscription.getId());

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_TRIAL_WILL_END");
        }
    }

    private void handleInvoicePaymentSucceeded(StripeWebhookEvent event) {
        if (event.getSubscriptionId() == null) {
            log.info("Invoice payment succeeded but no subscription ID - likely a one-time payment");
            return;
        }

        // Use pessimistic locking to prevent race conditions
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionIdWithLock(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();

            // Update expiration from invoice period
            if (event.getExpiresAt() != null) {
                subscription.setExpiresAt(event.getExpiresAt());
            }

            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCanceled(false);

            // Trial ended if payment succeeded
            if (Boolean.TRUE.equals(subscription.getIsTrialPeriod())) {
                subscription.setIsTrialPeriod(false);
                subscription.setTrialEndDate(null);
                log.info("Subscription {} trial ended, now paid", subscription.getId());
            }

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "PAYMENT_SUCCEEDED");

            // Send payment success notification to user
            // Stripe amounts are in cents, convert to dollars as BigDecimal
            BigDecimal amount = event.getAmount() != null
                    ? BigDecimal.valueOf(event.getAmount()).divide(BigDecimal.valueOf(100))
                    : subscription.getPrice();
            asyncNotificationHelper.sendPaymentSuccessNotificationAsync(
                    subscription.getUser(),
                    subscription,
                    amount
            );

            log.info("Payment succeeded for subscription {}", subscription.getId());
        }
    }

    private void handleInvoicePaymentFailed(StripeWebhookEvent event) {
        if (event.getSubscriptionId() == null) {
            return;
        }

        // Use pessimistic locking to prevent race conditions
        Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                .findByStripeSubscriptionIdWithLock(event.getSubscriptionId());

        if (subscriptionOpt.isPresent()) {
            UserSubscription subscription = subscriptionOpt.get();
            subscription.setStatus(SubscriptionStatus.PAYMENT_FAILED);

            userSubscriptionRepository.save(subscription);

            // Send payment failed notification asynchronously
            asyncNotificationHelper.sendPaymentFailedNotificationAsync(
                    subscription.getUser(),
                    subscription
            );

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "PAYMENT_FAILED");

            log.warn("Payment failed for subscription: {}", subscription.getId());
        }
    }

    private void handleChargeRefunded(StripeWebhookEvent event) {
        log.info("Charge refunded - paymentIntentId: {}, amount: {}",
                event.getPaymentIntentId(), event.getAmount());

        // Find subscription by payment intent if available
        if (event.getPaymentIntentId() != null) {
            // Log the refund for audit
            asyncNotificationHelper.sendWebhookAsync("CHARGE_REFUNDED", null);
        }

        // Note: Full refund handling would require finding the subscription
        // through the payment intent and potentially adjusting access
    }

    private void handleDisputeCreated(StripeWebhookEvent event) {
        log.warn("Dispute created - amount: {}, status: {}",
                event.getAmount(), event.getStatus());

        // Log the dispute for audit and alerting
        asyncNotificationHelper.sendWebhookAsync("DISPUTE_CREATED", null);

        // Disputes should be handled manually by support team
        // Consider sending alert to admin
    }
}
