package com.orbvpn.api.service.webhook;

import com.orbvpn.api.domain.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookEventCreator implements WebhookEventProvider {

    private Map<String, Object> createResellerInfo(Reseller reseller) {
        Map<String, Object> resellerInfo = new HashMap<>();
        resellerInfo.put("id", reseller.getId());
        resellerInfo.put("level", reseller.getLevel().getName().toString());
        resellerInfo.put("credit", reseller.getCredit().toString());

        // Add additional available reseller information
        if (reseller.getLevel() != null) {
            resellerInfo.put("levelId", reseller.getLevel().getId());
            resellerInfo.put("discountPercent", reseller.getLevel().getDiscountPercent());
        }

        return resellerInfo;
    }

    public Map<String, Object> createSubscriptionPayload(UserSubscription subscription) {
        Map<String, Object> payload = new HashMap<>();

        // User information
        User user = subscription.getUser();
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());

        // Add user profile info if exists
        UserProfile profile = user.getProfile();
        if (profile != null) {
            Map<String, Object> profileInfo = new HashMap<>();
            profileInfo.put("firstName", profile.getFirstName());
            profileInfo.put("lastName", profile.getLastName());
            profileInfo.put("address", profile.getAddress());
            profileInfo.put("city", profile.getCity());
            profileInfo.put("postalCode", profile.getPostalCode());
            profileInfo.put("country", profile.getCountry());
            profileInfo.put("birthDate", profile.getBirthDate());
            payload.put("profile", profileInfo);
        }

        // Initialize group explicitly to prevent lazy loading issues
        if (subscription.getGroup() != null) {
            Hibernate.initialize(subscription.getGroup());
        }

        // Subscription information
        Group group = subscription.getGroup();
        if (group != null) { // Ensure group is not null
            payload.put("subscriptionId", subscription.getId());
            payload.put("groupId", group.getId());
            payload.put("groupName", group.getName());
            payload.put("duration", String.valueOf(subscription.getDuration()));
            payload.put("expiresAt", subscription.getExpiresAt().toString());
            payload.put("createdAt", subscription.getCreatedAt().toString());
            payload.put("isTrialPeriod", subscription.getIsTrialPeriod());
            payload.put("multiLoginCount", subscription.getMultiLoginCount());
            payload.put("canceled", subscription.isCanceled());

            // Add bandwidth and download limits
            payload.put("dailyBandwidth", subscription.getDailyBandwidth());
            payload.put("downloadUpload", subscription.getDownloadUpload());

        } else {
            log.warn("Group is null for subscription id {}", subscription.getId());
        }

        // Payment information if available
        Payment payment = subscription.getPayment();
        if (payment != null) {
            Map<String, Object> paymentInfo = new HashMap<>();
            paymentInfo.put("id", payment.getId());
            paymentInfo.put("status", payment.getStatus().toString());
            paymentInfo.put("gateway", payment.getGateway().toString());
            paymentInfo.put("amount", payment.getPrice().toString());
            paymentInfo.put("category", payment.getCategory().toString());
            payload.put("payment", paymentInfo);
        }

        // Add gateway-specific identifiers
        if (subscription.getOriginalTransactionId() != null) {
            payload.put("originalTransactionId", subscription.getOriginalTransactionId());
            payload.put("gateway", "APPLE");
        } else if (subscription.getPurchaseToken() != null) {
            payload.put("purchaseToken", subscription.getPurchaseToken());
            payload.put("gateway", "GOOGLE_PLAY");
        }

        // Add reseller information if available
        Reseller reseller = user.getReseller();
        if (reseller != null) {
            payload.put("reseller", createResellerInfo(reseller));
        }

        // Add timestamps
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("eventTimestamp", System.currentTimeMillis());

        return payload;
    }

    public Map<String, Object> createUserPayload(User user, String action, UserSubscription currentSubscription) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        Map<String, Object> payload = new HashMap<>();

        // Basic user information
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("action", action);
        payload.put("active", user.isActive());
        payload.put("autoRenew", user.isAutoRenew());

        // User profile information
        UserProfile profile = user.getProfile();
        if (profile != null) {
            Map<String, Object> profileInfo = new HashMap<>();
            profileInfo.put("firstName", profile.getFirstName());
            profileInfo.put("lastName", profile.getLastName());
            profileInfo.put("country", profile.getCountry());
            profileInfo.put("phone", profile.getPhone());
            payload.put("profile", profileInfo);
        }

        // Current subscription information
        if (currentSubscription != null) {
            Group group = currentSubscription.getGroup();
            if (group != null) {
                Map<String, Object> subscriptionInfo = new HashMap<>();
                subscriptionInfo.put("id", currentSubscription.getId());
                subscriptionInfo.put("groupId", group.getId());
                subscriptionInfo.put("groupName", group.getName());
                subscriptionInfo.put("duration", String.valueOf(currentSubscription.getDuration()));
                subscriptionInfo.put("expiresAt", currentSubscription.getExpiresAt().toString());
                subscriptionInfo.put("multiLoginCount", currentSubscription.getMultiLoginCount());
                subscriptionInfo.put("isTrialPeriod", currentSubscription.getIsTrialPeriod());
                payload.put("subscription", subscriptionInfo);
            }
        }

        // Add reseller information if available
        Reseller reseller = user.getReseller();
        if (reseller != null) {
            payload.put("reseller", createResellerInfo(reseller));
        }

        // Add timestamps
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("eventTimestamp", System.currentTimeMillis());

        return payload;
    }

    public Map<String, Object> createPaymentPayload(Payment payment) {
        Map<String, Object> payload = new HashMap<>();

        // Basic payment information
        payload.put("paymentId", payment.getId());
        payload.put("status", payment.getStatus().toString());
        payload.put("gateway", payment.getGateway().toString());
        payload.put("amount", payment.getPrice().toString());
        payload.put("category", payment.getCategory().toString());
        payload.put("createdAt", payment.getCreatedAt().toString());

        if (payment.getExpiresAt() != null) {
            payload.put("expiresAt", payment.getExpiresAt().toString());
        }

        // User information
        User user = payment.getUser();
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        payload.put("user", userInfo);

        // Group information if available
        Integer groupId = payment.getGroupId();
        if (groupId != null) {
            payload.put("groupId", groupId);
        }

        // Additional payment details
        payload.put("moreLoginCount", payment.getMoreLoginCount());
        payload.put("renew", payment.isRenew());

        // Add timestamps
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("eventTimestamp", System.currentTimeMillis());

        // Add environment info
        payload.put("environment", "production"); // or get from configuration

        // Add payment reference info if available
        if (payment.getPaymentId() != null) {
            payload.put("paymentReference", payment.getPaymentId());
        }

        // Add additional payment metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processedAt", LocalDateTime.now().toString());
        payload.put("metadata", metadata);

        return payload;
    }

    public Map<String, Object> createInvoicePayload(Invoice invoice) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoiceId", invoice.getId());
        payload.put("status", invoice.getStatus());
        payload.put("amountPaid", invoice.getTotalAmount());
        payload.put("currency", invoice.getCurrency());
        payload.put("createdAt", invoice.getInvoiceDate().toString());
        payload.put("customer", invoice.getCustomer());
        payload.put("subscriptionId", invoice.getSubscriptionId());

        return payload;
    }

    public Map<String, Object> createPayloadWithExtra(User user, String action, Map<String, Object> extraData) {
        Map<String, Object> payload = createUserPayload(user, action);

        // Add extra data to the payload
        payload.putAll(extraData);

        // Add timestamps if not already present
        if (!payload.containsKey("timestamp")) {
            payload.put("timestamp", LocalDateTime.now().toString());
        }
        if (!payload.containsKey("eventTimestamp")) {
            payload.put("eventTimestamp", System.currentTimeMillis());
        }

        return payload;
    }

    @Override
    public Map<String, Object> createUserPayload(User user, String action) {
        return createUserPayload(user, action, null);
    }
}