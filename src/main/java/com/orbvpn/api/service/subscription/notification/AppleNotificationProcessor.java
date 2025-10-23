package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.domain.dto.AppleNotification;
import com.orbvpn.api.domain.dto.RenewalInfo;
import com.orbvpn.api.domain.dto.TransactionInfo;
import com.orbvpn.api.constants.AppleNotificationConstants.NotificationType;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.subscription.ProductGroupMapper;
import com.orbvpn.api.service.subscription.utils.TransactionMappingService;
import com.orbvpn.api.service.webhook.WebhookService;
import com.orbvpn.api.service.webhook.WebhookEventCreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleNotificationProcessor {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TransactionMappingService transactionMappingService;
    private final WebhookService webhookService;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final WebhookEventCreator webhookEventCreator;
    private final ObjectMapper objectMapper;
    private final ProductGroupMapper productGroupMapper;

    @Transactional
    public void processNotification(AppleNotification notification) {
        try {
            log.info("Processing Apple notification type: {}", notification.getNotificationType());

            if (notification.getData() == null) {
                log.error("Notification data is null");
                return;
            }

            TransactionInfo transactionInfo = extractTransactionInfo(notification);
            RenewalInfo renewalInfo = extractRenewalInfo(notification);

            if (transactionInfo == null && renewalInfo == null) {
                log.error("Both transaction info and renewal info are null");
                return;
            }

            String originalTransactionId = getOriginalTransactionId(transactionInfo, renewalInfo);
            if (originalTransactionId == null) {
                log.error("Missing originalTransactionId in notification");
                return;
            }

            UserSubscription subscription = userSubscriptionRepository
                    .findByOriginalTransactionId(originalTransactionId);
            if (subscription == null) {
                log.info("No subscription found for originalTransactionId: {}", originalTransactionId);
                User user = transactionMappingService.findUserByToken(originalTransactionId, GatewayName.APPLE_STORE);
                if (user == null) {
                    log.warn("No user found for transaction ID: {}", originalTransactionId);
                    return;
                }
                subscription = createNewSubscription(user, transactionInfo, originalTransactionId);
            }

            handleNotificationType(subscription, notification.getNotificationType(), transactionInfo, renewalInfo);

        } catch (Exception e) {
            log.error("Error processing Apple notification: {}", e.getMessage(), e);
        }
    }

    private TransactionInfo extractTransactionInfo(AppleNotification notification) {
        String signedTransactionInfo = notification.getData().getSignedTransactionInfo();
        if (signedTransactionInfo == null) {
            return null;
        }

        try {
            String[] parts = signedTransactionInfo.split("\\.");
            if (parts.length != 3) {
                log.error("Invalid JWT format: expected 3 parts, got {}", parts.length);
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, TransactionInfo.class);
        } catch (Exception e) {
            log.error("Error extracting transaction info: {}", e.getMessage(), e);
            return null;
        }
    }

    private RenewalInfo extractRenewalInfo(AppleNotification notification) {
        String signedRenewalInfo = notification.getData().getSignedRenewalInfo();
        if (signedRenewalInfo == null) {
            return null;
        }

        try {
            String[] parts = signedRenewalInfo.split("\\.");
            if (parts.length != 3) {
                log.error("Invalid JWT format: expected 3 parts, got {}", parts.length);
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, RenewalInfo.class);
        } catch (Exception e) {
            log.error("Error extracting renewal info: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getOriginalTransactionId(TransactionInfo transactionInfo, RenewalInfo renewalInfo) {
        if (transactionInfo != null && transactionInfo.getOriginalTransactionId() != null) {
            return transactionInfo.getOriginalTransactionId();
        }
        if (renewalInfo != null && renewalInfo.getOriginalTransactionId() != null) {
            return renewalInfo.getOriginalTransactionId();
        }
        return null;
    }

    @Transactional
    private UserSubscription createNewSubscription(User user, TransactionInfo transactionInfo,
            String originalTransactionId) {
        if (transactionInfo == null || transactionInfo.getProductId() == null) {
            log.error("Cannot create subscription without product ID");
            return null;
        }

        try {
            // Map product ID to group ID
            int groupId = productGroupMapper.mapProductIdToGroupId(transactionInfo.getProductId());
            Group group = groupService.getById(groupId);

            // Delete any existing subscriptions
            userSubscriptionRepository.deleteByUserId(user.getId());

            UserSubscription subscription = new UserSubscription();
            subscription.setUser(user);
            subscription.setGroup(group);
            subscription.setMultiLoginCount(group.getMultiLoginCount());
            subscription.setDuration(group.getDuration());
            subscription.setDailyBandwidth(group.getDailyBandwidth());
            subscription.setDownloadUpload(group.getDownloadUpload());
            subscription.setOriginalTransactionId(originalTransactionId);
            subscription.setAutoRenew(true);
            subscription.setGateway(GatewayName.APPLE_STORE);
            subscription.setStatus(SubscriptionStatus.ACTIVE);

            // Set expiration date if available
            if (transactionInfo.getExpiresDate() != null) {
                subscription.setExpiresAt(
                        Instant.ofEpochMilli(transactionInfo.getExpiresDate())
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime());
            } else {
                subscription.setExpiresAt(LocalDateTime.now().plusDays(group.getDuration()));
            }

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

    private void handleNotificationType(UserSubscription subscription, String notificationType,
            TransactionInfo transactionInfo, RenewalInfo renewalInfo) {
        try {
            switch (notificationType) {
                case NotificationType.INITIAL_BUY:
                    handleInitialBuy(subscription, transactionInfo);
                    break;
                case NotificationType.DID_RECOVER:
                    handleRecovery(subscription, transactionInfo);
                    break;
                case NotificationType.DID_RENEW:
                    handleRenewal(subscription, transactionInfo);
                    break;
                case NotificationType.EXPIRED:
                    handleExpiration(subscription);
                    break;
                case NotificationType.CANCEL:
                    handleCancellation(subscription);
                    break;
                case NotificationType.DID_FAIL_TO_RENEW:
                    handleRenewalFailure(subscription);
                    break;
                case NotificationType.DID_CHANGE_RENEWAL_STATUS:
                    handleRenewalStatusChange(subscription, renewalInfo);
                    break;
                case NotificationType.REFUND:
                    handleRefund(subscription);
                    break;
                case NotificationType.REVOKE:
                    handleRevoke(subscription);
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

    private void handleInitialBuy(UserSubscription subscription, TransactionInfo transactionInfo) {
        if (transactionInfo != null && transactionInfo.getExpiresDate() != null) {
            LocalDateTime expiresAt = Instant.ofEpochMilli(transactionInfo.getExpiresDate())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            subscription.setExpiresAt(expiresAt);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCanceled(false);
            subscription.setAutoRenew(true);

            webhookService.processWebhook("SUBSCRIPTION_CREATED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        }
    }

    private void handleRenewal(UserSubscription subscription, TransactionInfo transactionInfo) {
        if (transactionInfo != null && transactionInfo.getExpiresDate() != null) {
            LocalDateTime expiresAt = Instant.ofEpochMilli(transactionInfo.getExpiresDate())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            subscription.setExpiresAt(expiresAt);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCanceled(false);

            webhookService.processWebhook("SUBSCRIPTION_RENEWED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        }
    }

    private void handleRecovery(UserSubscription subscription, TransactionInfo transactionInfo) {
        if (transactionInfo != null && transactionInfo.getExpiresDate() != null) {
            LocalDateTime expiresAt = Instant.ofEpochMilli(transactionInfo.getExpiresDate())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();

            subscription.setExpiresAt(expiresAt);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCanceled(false);

            webhookService.processWebhook("SUBSCRIPTION_RECOVERED",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        }
    }

    private void handleExpiration(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiresAt(LocalDateTime.now());

        webhookService.processWebhook("SUBSCRIPTION_EXPIRED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleCancellation(UserSubscription subscription) {
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        webhookService.processWebhook("SUBSCRIPTION_CANCELLED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleRenewalFailure(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.GRACE_PERIOD);

        webhookService.processWebhook("SUBSCRIPTION_RENEWAL_FAILED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleRenewalStatusChange(UserSubscription subscription, RenewalInfo renewalInfo) {
        if (renewalInfo != null) {
            subscription.setAutoRenew(renewalInfo.getAutoRenewStatus() == 1);

            webhookService.processWebhook("DID_CHANGE_RENEWAL_STATUS",
                    webhookEventCreator.createSubscriptionPayload(subscription));
        }
    }

    private void handleRefund(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.REFUNDED);
        subscription.setExpiresAt(LocalDateTime.now());
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        webhookService.processWebhook("SUBSCRIPTION_REFUNDED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }

    private void handleRevoke(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.REVOKED);
        subscription.setExpiresAt(LocalDateTime.now());
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        webhookService.processWebhook("SUBSCRIPTION_REVOKED",
                webhookEventCreator.createSubscriptionPayload(subscription));
    }
}