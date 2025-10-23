package com.orbvpn.api.service.subscription;

import com.orbvpn.api.domain.dto.GooglePlaySubscriptionInfo;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.exception.SubscriptionException;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@Slf4j
public class GooglePlayService {
    private final AndroidPublisher publisher;
    private final Map<String, Integer> groupMap;
    private static final String PACKAGE_NAME = "com.orbvpn.android";

    public GooglePlayService(
            AndroidPublisher publisher,
            Map<String, Integer> groupMap) {
        this.publisher = publisher;
        this.groupMap = groupMap;
    }

    public GooglePlaySubscriptionInfo verifyTokenWithGooglePlay(
            String packageName,
            String purchaseToken,
            String subscriptionId,
            String deviceId,
            User user,
            Integer notificationType) {

        log.info("Verifying Google Play subscription for user: {}, subscriptionId: {}",
                user.getId(), subscriptionId);

        try {
            if (subscriptionId == null || subscriptionId.trim().isEmpty()) {
                throw new IllegalArgumentException("SubscriptionId cannot be null or empty");
            }

            if (packageName == null || packageName.trim().isEmpty()) {
                packageName = PACKAGE_NAME;
            }

            AndroidPublisher.Purchases.Subscriptions.Get request = publisher.purchases()
                    .subscriptions()
                    .get(packageName, subscriptionId, purchaseToken);

            SubscriptionPurchase purchase = request.execute();

            if (purchase == null) {
                throw new SubscriptionException("Invalid purchase data from Google Play");
            }

            LocalDateTime expiresAt = getExpirationDate(purchase, notificationType);
            Integer groupId = getGroupId(subscriptionId);
            boolean isTrialPeriod = purchase.getPaymentState() != null && purchase.getPaymentState() == 2;

            GooglePlaySubscriptionInfo info = GooglePlaySubscriptionInfo.builder()
                    .groupId(groupId)
                    .expiresAt(expiresAt)
                    .purchaseToken(purchaseToken)
                    .orderId(purchase.getOrderId())
                    .isTrialPeriod(isTrialPeriod)
                    .build();

            return info;

        } catch (Exception e) {
            log.error("Error verifying Google Play subscription: {}", e.getMessage(), e);
            throw new SubscriptionException("Error verifying subscription: " + e.getMessage());
        }
    }

    private Integer getGroupId(String subscriptionId) {
        Integer groupId = groupMap.get(subscriptionId);
        if (groupId == null) {
            throw new SubscriptionException("Unknown product SKU: " + subscriptionId);
        }
        return groupId;
    }

    private LocalDateTime getExpirationDate(SubscriptionPurchase purchase, Integer notificationType) {
        if (purchase.getExpiryTimeMillis() == null) {
            throw new IllegalArgumentException("Expiration timestamp cannot be null");
        }

        LocalDateTime expiresAt = Instant.ofEpochMilli(purchase.getExpiryTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        // Only validate future dates for new/active subscriptions
        // For notifications type 12 (REVOKED) and 13 (EXPIRED), allow past dates
        if (expiresAt.isBefore(LocalDateTime.now()) &&
                notificationType != null &&
                !isExpirationNotification(notificationType)) {
            log.warn("Subscription expiration date is in the past: {}", expiresAt);
        }

        return expiresAt;
    }

    private boolean isExpirationNotification(int notificationType) {
        return notificationType == 12 || // REVOKED
                notificationType == 13; // EXPIRED
    }

    public void acknowledgePurchase(String subscriptionId, String purchaseToken) {
        try {
            // First check if already acknowledged
            AndroidPublisher.Purchases.Subscriptions.Get getRequest = publisher.purchases()
                    .subscriptions()
                    .get(PACKAGE_NAME, subscriptionId, purchaseToken);

            SubscriptionPurchase purchase = getRequest.execute();
            if (purchase == null) {
                log.warn("Purchase not found for acknowledgment");
                return;
            }

            if (purchase.getAcknowledgementState() != null && purchase.getAcknowledgementState() == 1) {
                log.info("Purchase already acknowledged: {}", purchaseToken);
                return;
            }

            publisher.purchases()
                    .subscriptions()
                    .acknowledge(PACKAGE_NAME, subscriptionId, purchaseToken,
                            new com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest())
                    .execute();

            log.info("Successfully acknowledged purchase: {}", purchaseToken);
        } catch (Exception e) {
            if (e.getMessage() != null && (e.getMessage().contains("productNotOwnedByUser") ||
                    e.getMessage().contains("Purchase already acknowledged"))) {
                log.info("Acknowledgment not needed: {}", e.getMessage());
                return;
            }
            log.error("Error acknowledging purchase: {}", e.getMessage(), e);
        }
    }
}