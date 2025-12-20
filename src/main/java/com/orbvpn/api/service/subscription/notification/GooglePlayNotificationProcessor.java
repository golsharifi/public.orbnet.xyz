package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.ProcessedGoogleNotification;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.domain.dto.GooglePlaySubscriptionInfo;
import com.orbvpn.api.repository.ProcessedGoogleNotificationRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.subscription.GooglePlayService;
import com.orbvpn.api.service.subscription.utils.TransactionMappingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GooglePlayNotificationProcessor {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ProcessedGoogleNotificationRepository processedNotificationRepository;
    private final TransactionMappingService transactionMappingService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final GooglePlayService googlePlayService;

    @Transactional
    public void processNotification(GoogleNotification notification, String messageId) {
        ProcessedGoogleNotification processedNotification = null;

        try {
            // Check for duplicate notification (idempotency)
            if (processedNotificationRepository.existsByMessageId(messageId)) {
                log.info("Duplicate notification detected, messageId: {}", messageId);
                throw new IllegalStateException("Duplicate notification: " + messageId);
            }

            // Record that we're processing this notification
            processedNotification = new ProcessedGoogleNotification(messageId);

            if (notification.getSubscriptionNotification() == null) {
                log.info("Not a subscription notification, skipping");
                processedNotification.setStatus("SKIPPED");
                processedNotificationRepository.save(processedNotification);
                return;
            }

            GoogleNotification.SubscriptionNotification subNotification = notification.getSubscriptionNotification();
            String purchaseToken = subNotification.getPurchaseToken();
            String subscriptionId = subNotification.getSubscriptionId();
            int notificationType = subNotification.getNotificationType();

            // Record notification details
            processedNotification.setNotificationType(notificationType);
            processedNotification.setPurchaseToken(purchaseToken);
            processedNotification.setSubscriptionId(subscriptionId);

            log.info("Processing Google Play notification - Type: {}, SubscriptionId: {}, MessageId: {}",
                    getNotificationTypeName(notificationType), subscriptionId, messageId);

            if (purchaseToken == null || subscriptionId == null) {
                log.error("Missing required fields in notification");
                processedNotification.markFailed("Missing required fields");
                processedNotificationRepository.save(processedNotification);
                throw new IllegalArgumentException("Missing required fields in notification");
            }

            // Use pessimistic locking to prevent race conditions
            Optional<UserSubscription> existingSubscription =
                    userSubscriptionRepository.findByPurchaseTokenWithLock(purchaseToken);

            UserSubscription subscription;

            if (existingSubscription.isEmpty()) {
                // Try to find user by token
                User user = transactionMappingService.findUserByToken(purchaseToken, GatewayName.GOOGLE_PLAY);
                if (user == null) {
                    log.warn("No user found for token: {}", purchaseToken);
                    processedNotification.markFailed("No user found for token");
                    processedNotificationRepository.save(processedNotification);
                    return;
                }

                // Create new subscription if needed
                subscription = createNewSubscription(user, purchaseToken, subscriptionId);
                if (subscription == null) {
                    processedNotification.markFailed("Failed to create subscription");
                    processedNotificationRepository.save(processedNotification);
                    return;
                }
            } else {
                subscription = existingSubscription.get();
            }

            // Handle notification
            handleNotificationType(subscription, notificationType);

            // Try to acknowledge purchase
            googlePlayService.acknowledgePurchase(subscriptionId, purchaseToken);

            // Mark as successful
            processedNotification.markSuccess();
            processedNotificationRepository.save(processedNotification);

        } catch (IllegalStateException | IllegalArgumentException e) {
            // Re-throw these for controller to handle
            throw e;
        } catch (Exception e) {
            log.error("Error processing Google Play notification: {}", e.getMessage(), e);
            if (processedNotification != null) {
                processedNotification.markFailed(e.getMessage());
                processedNotificationRepository.save(processedNotification);
            }
            throw new RuntimeException("Error processing notification", e);
        }
    }

    private String getNotificationTypeName(int notificationType) {
        return switch (notificationType) {
            case 1 -> "RECOVERED";
            case 2 -> "RENEWED";
            case 3 -> "CANCELED";
            case 4 -> "PURCHASED";
            case 5 -> "ON_HOLD";
            case 6 -> "IN_GRACE_PERIOD";
            case 7 -> "RESTARTED";
            case 8 -> "PRICE_CHANGE_CONFIRMED";
            case 9 -> "DEFERRED";
            case 10 -> "PAUSED";
            case 11 -> "PAUSE_SCHEDULE_CHANGED";
            case 12 -> "REVOKED";
            case 13 -> "EXPIRED";
            case 20 -> "PENDING_PURCHASE_CANCELED";
            default -> "UNKNOWN_" + notificationType;
        };
    }

    @Transactional
    private UserSubscription createNewSubscription(User user, String purchaseToken, String subscriptionId) {
        try {
            // Get subscription info from Google
            GooglePlaySubscriptionInfo info = googlePlayService.verifyTokenWithGooglePlay(
                    "com.orbvpn.android",
                    purchaseToken,
                    subscriptionId,
                    "unknown",
                    user,
                    null);

            if (info == null) {
                log.error("Failed to get subscription info from Google");
                return null;
            }

            // Delete any existing subscriptions
            userSubscriptionRepository.deleteByUserId(user.getId());

            Group group = groupService.getById(info.getGroupId());

            UserSubscription subscription = new UserSubscription();
            subscription.setUser(user);
            subscription.setGroup(group);
            subscription.setMultiLoginCount(group.getMultiLoginCount());
            subscription.setDuration(group.getDuration());
            subscription.setDailyBandwidth(group.getDailyBandwidth());
            subscription.setDownloadUpload(group.getDownloadUpload());
            subscription.setPurchaseToken(purchaseToken);
            subscription.setSubscriptionId(subscriptionId);
            subscription.setOrderId(info.getOrderId());
            subscription.setExpiresAt(info.getExpiresAt());
            subscription.setAutoRenew(true);
            subscription.setGateway(GatewayName.GOOGLE_PLAY);
            subscription.setStatus(SubscriptionStatus.ACTIVE);

            UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
            radiusService.createUserRadChecks(savedSubscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(savedSubscription, "SUBSCRIPTION_CREATED");

            return savedSubscription;
        } catch (Exception e) {
            log.error("Error creating subscription for user {}: {}", user.getId(), e.getMessage(), e);
            return null;
        }
    }

    private void handleNotificationType(UserSubscription subscription, int notificationType) {
        try {
            switch (notificationType) {
                case 1: // RECOVERED
                    handleRecovered(subscription);
                    break;
                case 2: // RENEWED
                    handleRenewed(subscription);
                    break;
                case 3: // CANCELED
                    handleCanceled(subscription);
                    break;
                case 4: // PURCHASED
                    handlePurchased(subscription);
                    break;
                case 5: // ON_HOLD
                    handleOnHold(subscription);
                    break;
                case 6: // IN_GRACE_PERIOD
                    handleGracePeriod(subscription);
                    break;
                case 7: // RESTARTED
                    handleRestarted(subscription);
                    break;
                case 8: // PRICE_CHANGE_CONFIRMED
                    handlePriceChangeConfirmed(subscription);
                    break;
                case 9: // DEFERRED
                    handleDeferred(subscription);
                    break;
                case 10: // PAUSED
                    handlePaused(subscription);
                    break;
                case 11: // PAUSE_SCHEDULE_CHANGED
                    handlePauseScheduleChanged(subscription);
                    break;
                case 12: // REVOKED
                    handleRevoked(subscription);
                    break;
                case 13: // EXPIRED
                    handleExpired(subscription);
                    break;
                case 20: // PENDING_PURCHASE_CANCELED
                    handlePendingPurchaseCanceled(subscription);
                    break;
                default:
                    log.info("Unhandled notification type: {}", notificationType);
            }

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

        } catch (Exception e) {
            log.error("Error handling notification type {}: {}", notificationType, e.getMessage(), e);
        }
    }

    private void handleRenewed(UserSubscription subscription) {
        // Update the expiration date by getting the latest info
        try {
            GooglePlaySubscriptionInfo info = googlePlayService.verifyTokenWithGooglePlay(
                    "com.orbvpn.android",
                    subscription.getPurchaseToken(),
                    subscription.getSubscriptionId(),
                    "unknown",
                    subscription.getUser(),
                    null);

            if (info != null && info.getExpiresAt() != null) {
                subscription.setExpiresAt(info.getExpiresAt());
            } else {
                // Default to extending by the group duration
                subscription.setExpiresAt(LocalDateTime.now().plusDays(subscription.getDuration()));
            }

            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCanceled(false);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_RENEWED");
        } catch (Exception e) {
            log.error("Error updating expiration date for renewal: {}", e.getMessage(), e);
        }
    }

    private void handleCanceled(UserSubscription subscription) {
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CANCELLED");
    }

    private void handlePurchased(UserSubscription subscription) {
        // Update subscription status and auto-renew
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCanceled(false);
        subscription.setAutoRenew(true);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_PURCHASED");
    }

    private void handleOnHold(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.ON_HOLD);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_ON_HOLD");
    }

    private void handleGracePeriod(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.GRACE_PERIOD);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_GRACE_PERIOD");
    }

    private void handleRestarted(UserSubscription subscription) {
        // Update the expiration date
        try {
            GooglePlaySubscriptionInfo info = googlePlayService.verifyTokenWithGooglePlay(
                    "com.orbvpn.android",
                    subscription.getPurchaseToken(),
                    subscription.getSubscriptionId(),
                    "unknown",
                    subscription.getUser(),
                    null);

            if (info != null && info.getExpiresAt() != null) {
                subscription.setExpiresAt(info.getExpiresAt());
            } else {
                subscription.setExpiresAt(LocalDateTime.now().plusDays(subscription.getDuration()));
            }

            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCanceled(false);
            subscription.setAutoRenew(true);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_RESTARTED");
        } catch (Exception e) {
            log.error("Error updating expiration date for restart: {}", e.getMessage(), e);
        }
    }

    private void handlePaused(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.PAUSED);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_PAUSED");
    }

    private void handleRevoked(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.REVOKED);
        subscription.setExpiresAt(LocalDateTime.now());
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_REVOKED");
    }

    private void handleExpired(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiresAt(LocalDateTime.now());

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_EXPIRED");
    }

    private void handleRecovered(UserSubscription subscription) {
        // Update the expiration date
        try {
            GooglePlaySubscriptionInfo info = googlePlayService.verifyTokenWithGooglePlay(
                    "com.orbvpn.android",
                    subscription.getPurchaseToken(),
                    subscription.getSubscriptionId(),
                    "unknown",
                    subscription.getUser(),
                    null);

            if (info != null && info.getExpiresAt() != null) {
                subscription.setExpiresAt(info.getExpiresAt());
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setCanceled(false);
                subscription.setAutoRenew(true);

                asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_RECOVERED");
            }
        } catch (Exception e) {
            log.error("Error updating expiration date for recovery: {}", e.getMessage(), e);
        }
    }

    private void handlePriceChangeConfirmed(UserSubscription subscription) {
        // User has confirmed a price change - subscription continues at new price
        log.info("Price change confirmed for subscription: {}", subscription.getId());
        // No status change needed, just log and webhook
        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_PRICE_CHANGE_CONFIRMED");
    }

    private void handleDeferred(UserSubscription subscription) {
        // Subscription renewal has been deferred (extended without payment)
        // Update the expiration date from Google
        try {
            GooglePlaySubscriptionInfo info = googlePlayService.verifyTokenWithGooglePlay(
                    "com.orbvpn.android",
                    subscription.getPurchaseToken(),
                    subscription.getSubscriptionId(),
                    "unknown",
                    subscription.getUser(),
                    null);

            if (info != null && info.getExpiresAt() != null) {
                subscription.setExpiresAt(info.getExpiresAt());
                log.info("Subscription {} deferred, new expiry: {}", subscription.getId(), info.getExpiresAt());
            }

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_DEFERRED");
        } catch (Exception e) {
            log.error("Error handling deferred subscription: {}", e.getMessage(), e);
        }
    }

    private void handlePauseScheduleChanged(UserSubscription subscription) {
        // The pause schedule has changed - user either scheduled or cancelled a pause
        log.info("Pause schedule changed for subscription: {}", subscription.getId());
        // No immediate status change needed
        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED");
    }

    private void handlePendingPurchaseCanceled(UserSubscription subscription) {
        // A pending purchase was canceled before it could be completed
        log.info("Pending purchase canceled for subscription: {}", subscription.getId());
        // If subscription is not yet active, clean it up
        if (subscription.getStatus() == null ||
            subscription.getStatus() == SubscriptionStatus.PENDING) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setCanceled(true);
            subscription.setAutoRenew(false);
        }
        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_PENDING_PURCHASE_CANCELED");
    }
}