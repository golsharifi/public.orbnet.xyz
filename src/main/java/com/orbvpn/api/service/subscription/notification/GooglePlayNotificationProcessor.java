package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.domain.dto.GooglePlaySubscriptionInfo;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.subscription.GooglePlayService;
import com.orbvpn.api.service.subscription.utils.TransactionMappingService;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class GooglePlayNotificationProcessor {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TransactionMappingService transactionMappingService;
    private final WebhookService webhookService;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final GooglePlayService googlePlayService;
    private final WebhookEventCreator webhookEventCreator;

    @Transactional
    public void processNotification(GoogleNotification notification) {
        try {
            if (notification.getSubscriptionNotification() == null) {
                log.info("Not a subscription notification, skipping");
                return;
            }

            GoogleNotification.SubscriptionNotification subNotification = notification.getSubscriptionNotification();
            String purchaseToken = subNotification.getPurchaseToken();
            String subscriptionId = subNotification.getSubscriptionId();
            int notificationType = subNotification.getNotificationType();

            log.info("Processing Google Play notification - Type: {}, SubscriptionId: {}",
                    getNotificationTypeName(notificationType), subscriptionId);

            if (purchaseToken == null || subscriptionId == null) {
                log.error("Missing required fields in notification");
                return;
            }

            // First try to find existing subscription
            UserSubscription subscription = userSubscriptionRepository.findByPurchaseToken(purchaseToken);

            if (subscription == null) {
                // Try to find user by token
                User user = transactionMappingService.findUserByToken(purchaseToken, GatewayName.GOOGLE_PLAY);
                if (user == null) {
                    log.warn("No user found for token: {}", purchaseToken);
                    return;
                }

                // Create new subscription if needed
                subscription = createNewSubscription(user, purchaseToken, subscriptionId);
            }

            // Handle notification
            handleNotificationType(subscription, notificationType);

            // Try to acknowledge purchase
            googlePlayService.acknowledgePurchase(subscriptionId, purchaseToken);

        } catch (Exception e) {
            log.error("Error processing Google Play notification: {}", e.getMessage(), e);
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
            case 10 -> "PAUSED";
            case 12 -> "REVOKED";
            case 13 -> "EXPIRED";
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

            webhookService.processWebhook("SUBSCRIPTION_CREATED",
                    webhookEventCreator.createSubscriptionPayload(savedSubscription));

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
                case 10: // PAUSED
                    handlePaused(subscription);
                    break;
                case 12: // REVOKED
                    handleRevoked(subscription);
                    break;
                case 13: // EXPIRED
                    handleExpired(subscription);
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

            webhookService.processWebhook("SUBSCRIPTION_RENEWED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        } catch (Exception e) {
            log.error("Error updating expiration date for renewal: {}", e.getMessage(), e);
        }
    }

    private void handleCanceled(UserSubscription subscription) {
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        webhookService.processWebhook("SUBSCRIPTION_CANCELLED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handlePurchased(UserSubscription subscription) {
        // Update subscription status and auto-renew
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCanceled(false);
        subscription.setAutoRenew(true);

        webhookService.processWebhook("SUBSCRIPTION_PURCHASED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleOnHold(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.ON_HOLD);

        webhookService.processWebhook("SUBSCRIPTION_ON_HOLD",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleGracePeriod(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.GRACE_PERIOD);

        webhookService.processWebhook("SUBSCRIPTION_GRACE_PERIOD",
                webhookEventCreator.createSubscriptionPayload(subscription));
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

            webhookService.processWebhook("SUBSCRIPTION_RESTARTED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        } catch (Exception e) {
            log.error("Error updating expiration date for restart: {}", e.getMessage(), e);
        }
    }

    private void handlePaused(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.PAUSED);

        webhookService.processWebhook("SUBSCRIPTION_PAUSED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleRevoked(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.REVOKED);
        subscription.setExpiresAt(LocalDateTime.now());
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        webhookService.processWebhook("SUBSCRIPTION_REVOKED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleExpired(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiresAt(LocalDateTime.now());

        webhookService.processWebhook("SUBSCRIPTION_EXPIRED",
                webhookEventCreator.createSubscriptionPayload(subscription));
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

                webhookService.processWebhook("SUBSCRIPTION_RECOVERED",
                        webhookEventCreator.createSubscriptionPayload(subscription));
            }
        } catch (Exception e) {
            log.error("Error updating expiration date for recovery: {}", e.getMessage(), e);
        }
    }
}