package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.entity.ProcessedAppleNotification;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.domain.dto.AppleNotification;
import com.orbvpn.api.domain.dto.RenewalInfo;
import com.orbvpn.api.domain.dto.TransactionInfo;
import com.orbvpn.api.constants.AppleNotificationConstants.NotificationType;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.repository.ProcessedAppleNotificationRepository;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.subscription.ProductGroupMapper;
import com.orbvpn.api.service.subscription.AppleJwtVerificationService;
import com.orbvpn.api.service.subscription.utils.TransactionMappingService;
import com.orbvpn.api.service.AsyncNotificationHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleNotificationProcessor {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ProcessedAppleNotificationRepository processedNotificationRepository;
    private final TransactionMappingService transactionMappingService;
    private final AsyncNotificationHelper asyncNotificationHelper;
    private final GroupService groupService;
    private final RadiusService radiusService;
    private final ObjectMapper objectMapper;
    private final ProductGroupMapper productGroupMapper;
    private final AppleJwtVerificationService appleJwtVerificationService;

    @Transactional
    public void processNotification(AppleNotification notification) {
        String notificationUUID = notification.getNotificationUUID();
        ProcessedAppleNotification processedRecord = null;

        try {
            log.info("Processing Apple notification type: {} UUID: {}",
                    notification.getNotificationType(), notificationUUID);

            // Idempotency check - skip if already processed
            if (notificationUUID != null && !notificationUUID.isEmpty()) {
                if (processedNotificationRepository.existsByNotificationUUID(notificationUUID)) {
                    log.info("Apple notification {} already processed, skipping", notificationUUID);
                    return;
                }

                // Create tracking record
                processedRecord = new ProcessedAppleNotification(notificationUUID);
                processedRecord.setNotificationType(notification.getNotificationType());
                processedNotificationRepository.save(processedRecord);
            }

            if (notification.getData() == null) {
                log.error("Notification data is null");
                markNotificationFailed(processedRecord, "Notification data is null");
                return;
            }

            // Extract and verify transaction/renewal info using JWT verification
            TransactionInfo transactionInfo = extractTransactionInfo(notification);
            RenewalInfo renewalInfo = extractRenewalInfo(notification);

            if (transactionInfo == null && renewalInfo == null) {
                log.error("Both transaction info and renewal info are null");
                markNotificationFailed(processedRecord, "Both transaction info and renewal info are null");
                return;
            }

            String originalTransactionId = getOriginalTransactionId(transactionInfo, renewalInfo);
            if (originalTransactionId == null || originalTransactionId.trim().isEmpty()) {
                log.error("Missing or empty originalTransactionId in notification");
                markNotificationFailed(processedRecord, "Missing originalTransactionId");
                return;
            }

            if (processedRecord != null) {
                processedRecord.setOriginalTransactionId(originalTransactionId);
            }

            // Use pessimistic locking to prevent race conditions
            Optional<UserSubscription> subscriptionOpt = userSubscriptionRepository
                    .findByOriginalTransactionIdWithLock(originalTransactionId);

            UserSubscription subscription;
            if (subscriptionOpt.isEmpty()) {
                log.info("No subscription found for originalTransactionId: {}", originalTransactionId);
                User user = transactionMappingService.findUserByToken(originalTransactionId, GatewayName.APPLE_STORE);
                if (user == null) {
                    log.warn("No user found for transaction ID: {}", originalTransactionId);
                    markNotificationFailed(processedRecord, "No user found for transaction ID");
                    return;
                }
                subscription = createNewSubscription(user, transactionInfo, originalTransactionId);
                if (subscription == null) {
                    markNotificationFailed(processedRecord, "Failed to create subscription");
                    return;
                }
            } else {
                subscription = subscriptionOpt.get();
            }

            handleNotificationType(subscription, notification.getNotificationType(), transactionInfo, renewalInfo);

            // Mark as successfully processed
            markNotificationSuccess(processedRecord);

        } catch (Exception e) {
            log.error("Error processing Apple notification: {}", e.getMessage(), e);
            markNotificationFailed(processedRecord, e.getMessage());
            throw new RuntimeException("Apple notification processing failed", e);
        }
    }

    private void markNotificationSuccess(ProcessedAppleNotification record) {
        if (record != null) {
            record.markSuccess();
            processedNotificationRepository.save(record);
        }
    }

    private void markNotificationFailed(ProcessedAppleNotification record, String errorMessage) {
        if (record != null) {
            record.markFailed(errorMessage);
            processedNotificationRepository.save(record);
        }
    }

    private TransactionInfo extractTransactionInfo(AppleNotification notification) {
        String signedTransactionInfo = notification.getData().getSignedTransactionInfo();
        if (signedTransactionInfo == null) {
            return null;
        }

        try {
            // Use JWT verification service to verify and decode
            String verifiedPayload = appleJwtVerificationService.verifyAndDecodeSignedData(signedTransactionInfo);
            if (verifiedPayload == null) {
                return null;
            }
            return objectMapper.readValue(verifiedPayload, TransactionInfo.class);
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
            // Use JWT verification service to verify and decode
            String verifiedPayload = appleJwtVerificationService.verifyAndDecodeSignedData(signedRenewalInfo);
            if (verifiedPayload == null) {
                return null;
            }
            return objectMapper.readValue(verifiedPayload, RenewalInfo.class);
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

            // Calculate expiration date - use UTC for consistency
            LocalDateTime newExpiresAt;
            if (transactionInfo.getExpiresDate() != null) {
                newExpiresAt = Instant.ofEpochMilli(transactionInfo.getExpiresDate())
                        .atZone(ZoneOffset.UTC)
                        .toLocalDateTime();
            } else {
                newExpiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(group.getDuration());
            }

            // Check for existing subscription - extend instead of replacing if same group
            Optional<UserSubscription> existingOpt = userSubscriptionRepository.findByUserIdWithLock((long) user.getId());
            if (existingOpt.isPresent()) {
                UserSubscription existing = existingOpt.get();

                if (existing.getGroup().getId() == group.getId()) {
                    // Same group - extend from current expiration (industry standard)
                    LocalDateTime currentExpiry = existing.getExpiresAt();
                    if (currentExpiry != null && currentExpiry.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                        // User has remaining time - Apple already calculates the extended date
                        // Use Apple's expiration date as it accounts for remaining time
                        log.info("Extending Apple subscription for user {} to {}", user.getId(), newExpiresAt);
                    }
                } else {
                    log.info("User {} changing plan from group {} to group {}",
                            user.getId(), existing.getGroup().getId(), group.getId());
                }

                // Delete old subscription to create new one
                userSubscriptionRepository.deleteByUserId(user.getId());
                userSubscriptionRepository.flush();
            }

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
            subscription.setExpiresAt(newExpiresAt);

            UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);
            radiusService.createUserRadChecks(savedSubscription);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(savedSubscription, "SUBSCRIPTION_CREATED");

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
                case NotificationType.DID_CHANGE_RENEWAL_PREF:
                    handleRenewalPrefChange(subscription, renewalInfo, transactionInfo);
                    break;
                case NotificationType.PRICE_INCREASE:
                    handlePriceIncrease(subscription, renewalInfo);
                    break;
                case NotificationType.REFUND:
                    handleRefund(subscription);
                    break;
                case NotificationType.REVOKE:
                    handleRevoke(subscription);
                    break;
                default:
                    log.warn("Unhandled Apple notification type: {}", notificationType);
            }

            userSubscriptionRepository.save(subscription);
            radiusService.updateUserExpirationRadCheck(subscription);

        } catch (Exception e) {
            log.error("Error handling notification type {}: {}", notificationType, e.getMessage(), e);
            throw e; // Re-throw to mark notification as failed
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

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CREATED");
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

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_RENEWED");
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

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_RECOVERED");
        }
    }

    private void handleExpiration(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiresAt(LocalDateTime.now());

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_EXPIRED");
    }

    private void handleCancellation(UserSubscription subscription) {
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_CANCELLED");
    }

    private void handleRenewalFailure(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.GRACE_PERIOD);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_RENEWAL_FAILED");
    }

    private void handleRenewalStatusChange(UserSubscription subscription, RenewalInfo renewalInfo) {
        if (renewalInfo != null) {
            subscription.setAutoRenew(renewalInfo.getAutoRenewStatus() == 1);

            asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "DID_CHANGE_RENEWAL_STATUS");
        }
    }

    private void handleRefund(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.REFUNDED);
        subscription.setExpiresAt(LocalDateTime.now());
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_REFUNDED");
    }

    private void handleRevoke(UserSubscription subscription) {
        subscription.setStatus(SubscriptionStatus.REVOKED);
        subscription.setExpiresAt(LocalDateTime.now());
        subscription.setCanceled(true);
        subscription.setAutoRenew(false);

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "SUBSCRIPTION_REVOKED");
    }

    /**
     * Handles DID_CHANGE_RENEWAL_PREF notification.
     * Sent when the user changes their subscription renewal preferences,
     * such as upgrading/downgrading to a different product.
     */
    private void handleRenewalPrefChange(UserSubscription subscription, RenewalInfo renewalInfo,
            TransactionInfo transactionInfo) {
        log.info("User {} changed renewal preferences", subscription.getUser().getId());

        if (renewalInfo != null) {
            // Auto-renew product ID indicates what they will renew to
            String autoRenewProductId = renewalInfo.getAutoRenewProductId();
            if (autoRenewProductId != null && !autoRenewProductId.isEmpty()) {
                try {
                    int newGroupId = productGroupMapper.mapProductIdToGroupId(autoRenewProductId);
                    groupService.getById(newGroupId); // Verify group exists

                    // Store pending group change - will be applied on next renewal
                    // Note: For now, we just log it. A more complete implementation
                    // would store pendingGroupId on the subscription entity
                    log.info("User {} scheduled plan change to group {} on next renewal",
                            subscription.getUser().getId(), newGroupId);
                } catch (Exception e) {
                    log.warn("Failed to map auto-renew product ID {}: {}",
                            autoRenewProductId, e.getMessage());
                }
            }

            subscription.setAutoRenew(renewalInfo.getAutoRenewStatus() == 1);
        }

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "DID_CHANGE_RENEWAL_PREF");
    }

    /**
     * Handles PRICE_INCREASE notification.
     * Sent when Apple increases the subscription price and needs user consent.
     * The user has already been notified by Apple and may need to consent.
     */
    private void handlePriceIncrease(UserSubscription subscription, RenewalInfo renewalInfo) {
        log.info("Price increase notification for user {}", subscription.getUser().getId());

        if (renewalInfo != null) {
            Integer priceConsentStatus = renewalInfo.getPriceConsentStatus();
            if (priceConsentStatus != null) {
                // 0 = user hasn't responded, 1 = user consented
                boolean userConsented = priceConsentStatus == 1;
                log.info("Price consent status for user {}: {}",
                        subscription.getUser().getId(),
                        userConsented ? "CONSENTED" : "PENDING");

                if (!userConsented) {
                    // User hasn't consented yet - subscription may lapse if they don't
                    log.warn("User {} has not consented to price increase - subscription at risk",
                            subscription.getUser().getId());
                }
            }
        }

        asyncNotificationHelper.sendSubscriptionWebhookAsync(subscription, "PRICE_INCREASE");
    }
}