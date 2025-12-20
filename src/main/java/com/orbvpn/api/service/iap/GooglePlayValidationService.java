package com.orbvpn.api.service.iap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import com.google.api.services.androidpublisher.model.SubscriptionPurchase;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;

/**
 * Service for validating Google Play purchases using the Android Publisher API.
 *
 * Google Play Validation Flow:
 * 1. Receive purchase token from client
 * 2. Call Google Play Developer API to verify purchase
 * 3. Return validated purchase info
 *
 * Documentation: https://developers.google.com/android-publisher/api-ref/rest
 *
 * Prerequisites:
 * 1. Create a service account in Google Cloud Console
 * 2. Download the JSON key file
 * 3. Link the service account to Google Play Console with proper permissions
 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Lazy
public class GooglePlayValidationService {

    private final ObjectMapper objectMapper;

    @Value("${google.play.package-name:com.orbvpn.app}")
    private String packageName;

    @Value("${google.play.service-account-json:}")
    private String serviceAccountJsonPath;

    @Value("${google.play.service-account-base64:}")
    private String serviceAccountBase64;

    private AndroidPublisher androidPublisher;
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            GoogleCredentials credentials = getCredentials();
            if (credentials == null) {
                log.warn("Google Play credentials not configured. Validation will be disabled.");
                return;
            }

            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(
                    credentials.createScoped(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
            );

            androidPublisher = new AndroidPublisher.Builder(
                    httpTransport,
                    GsonFactory.getDefaultInstance(),
                    requestInitializer
            )
                    .setApplicationName("OrbVPN")
                    .build();

            initialized = true;
            log.info("Google Play validation service initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize Google Play validation service: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if the service is properly initialized.
     */
    public boolean isAvailable() {
        return initialized && androidPublisher != null;
    }

    /**
     * Validate a subscription purchase.
     *
     * @param productId     The subscription product ID
     * @param purchaseToken The purchase token from Google Play
     * @return Validation result
     */
    public GooglePlayValidationResult validateSubscription(String productId, String purchaseToken) {
        log.info("Validating Google Play subscription: product={}", productId);

        if (!isAvailable()) {
            log.error("Google Play validation service not available");
            return GooglePlayValidationResult.failure("Google Play validation not configured");
        }

        try {
            SubscriptionPurchase subscription = androidPublisher.purchases()
                    .subscriptions()
                    .get(packageName, productId, purchaseToken)
                    .execute();

            // Check payment state
            // 0 = Pending, 1 = Received, 2 = Free trial, 3 = Pending deferred upgrade/downgrade
            Integer paymentState = subscription.getPaymentState();
            if (paymentState == null || (paymentState != 1 && paymentState != 2)) {
                log.warn("Invalid payment state: {}", paymentState);
                return GooglePlayValidationResult.failure("Payment not completed");
            }

            // Check if cancelled
            Integer cancelReason = subscription.getCancelReason();
            boolean isCancelled = cancelReason != null;

            // Parse expiry time
            Long expiryTimeMillis = subscription.getExpiryTimeMillis();
            LocalDateTime expiresAt = null;
            boolean isExpired = false;
            if (expiryTimeMillis != null) {
                expiresAt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(expiryTimeMillis),
                        ZoneId.systemDefault()
                );
                isExpired = expiryTimeMillis < System.currentTimeMillis();
            }

            // Parse start time
            Long startTimeMillis = subscription.getStartTimeMillis();
            LocalDateTime startedAt = null;
            if (startTimeMillis != null) {
                startedAt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(startTimeMillis),
                        ZoneId.systemDefault()
                );
            }

            // Check acknowledgement
            Boolean acknowledged = subscription.getAcknowledgementState() == 1;

            log.info("Google Play subscription validated: product={}, expired={}, cancelled={}",
                    productId, isExpired, isCancelled);

            return GooglePlayValidationResult.builder()
                    .valid(true)
                    .productId(productId)
                    .purchaseToken(purchaseToken)
                    .orderId(subscription.getOrderId())
                    .startTime(startedAt)
                    .expiryTime(expiresAt)
                    .isExpired(isExpired)
                    .isCancelled(isCancelled)
                    .cancelReason(cancelReason != null ? cancelReason.intValue() : null)
                    .isAutoRenewing(Boolean.TRUE.equals(subscription.getAutoRenewing()))
                    .paymentState(paymentState)
                    .priceAmountMicros(subscription.getPriceAmountMicros())
                    .priceCurrencyCode(subscription.getPriceCurrencyCode())
                    .countryCode(subscription.getCountryCode())
                    .developerPayload(subscription.getDeveloperPayload())
                    .linkedPurchaseToken(subscription.getLinkedPurchaseToken())
                    .isAcknowledged(acknowledged)
                    .purchaseType(PurchaseType.SUBSCRIPTION)
                    .build();

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            log.error("Google Play API error: {} - {}", e.getStatusCode(), e.getDetails());
            return GooglePlayValidationResult.failure("Google Play API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error validating Google Play subscription: {}", e.getMessage(), e);
            return GooglePlayValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate a one-time product purchase (consumable or non-consumable).
     *
     * @param productId     The product ID
     * @param purchaseToken The purchase token from Google Play
     * @return Validation result
     */
    public GooglePlayValidationResult validateProduct(String productId, String purchaseToken) {
        log.info("Validating Google Play product purchase: product={}", productId);

        if (!isAvailable()) {
            log.error("Google Play validation service not available");
            return GooglePlayValidationResult.failure("Google Play validation not configured");
        }

        try {
            ProductPurchase product = androidPublisher.purchases()
                    .products()
                    .get(packageName, productId, purchaseToken)
                    .execute();

            // Check purchase state
            // 0 = Purchased, 1 = Canceled, 2 = Pending
            Integer purchaseState = product.getPurchaseState();
            if (purchaseState == null || purchaseState != 0) {
                log.warn("Invalid purchase state: {}", purchaseState);
                return GooglePlayValidationResult.failure("Purchase not completed");
            }

            // Check consumption state for consumables
            // 0 = Yet to be consumed, 1 = Consumed
            Integer consumptionState = product.getConsumptionState();

            // Parse purchase time
            Long purchaseTimeMillis = product.getPurchaseTimeMillis();
            LocalDateTime purchasedAt = null;
            if (purchaseTimeMillis != null) {
                purchasedAt = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(purchaseTimeMillis),
                        ZoneId.systemDefault()
                );
            }

            // Check acknowledgement
            Integer acknowledgementState = product.getAcknowledgementState();
            boolean acknowledged = acknowledgementState != null && acknowledgementState == 1;

            log.info("Google Play product validated: product={}, consumed={}",
                    productId, consumptionState == 1);

            return GooglePlayValidationResult.builder()
                    .valid(true)
                    .productId(productId)
                    .purchaseToken(purchaseToken)
                    .orderId(product.getOrderId())
                    .startTime(purchasedAt)
                    .purchaseState(purchaseState)
                    .consumptionState(consumptionState)
                    .developerPayload(product.getDeveloperPayload())
                    .isAcknowledged(acknowledged)
                    .purchaseType(PurchaseType.ONE_TIME)
                    .regionCode(product.getRegionCode())
                    .build();

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            log.error("Google Play API error: {} - {}", e.getStatusCode(), e.getDetails());
            return GooglePlayValidationResult.failure("Google Play API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error validating Google Play product: {}", e.getMessage(), e);
            return GooglePlayValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Acknowledge a subscription purchase.
     * Must be called within 3 days of purchase to prevent refund.
     */
    public boolean acknowledgeSubscription(String productId, String purchaseToken) {
        if (!isAvailable()) {
            log.error("Google Play validation service not available for acknowledgement");
            return false;
        }

        try {
            androidPublisher.purchases()
                    .subscriptions()
                    .acknowledge(packageName, productId, purchaseToken, null)
                    .execute();

            log.info("Subscription acknowledged: product={}", productId);
            return true;
        } catch (Exception e) {
            log.error("Error acknowledging subscription: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Acknowledge a product purchase.
     */
    public boolean acknowledgeProduct(String productId, String purchaseToken) {
        if (!isAvailable()) {
            log.error("Google Play validation service not available for acknowledgement");
            return false;
        }

        try {
            androidPublisher.purchases()
                    .products()
                    .acknowledge(packageName, productId, purchaseToken, null)
                    .execute();

            log.info("Product acknowledged: product={}", productId);
            return true;
        } catch (Exception e) {
            log.error("Error acknowledging product: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get credentials from file or base64 encoded string.
     */
    private GoogleCredentials getCredentials() throws Exception {
        InputStream credentialsStream = null;

        // Try base64 encoded credentials first (for Docker/K8s secrets)
        if (serviceAccountBase64 != null && !serviceAccountBase64.isEmpty()) {
            byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64);
            credentialsStream = new ByteArrayInputStream(decoded);
            log.info("Using base64 encoded Google credentials");
        }
        // Try file path
        else if (serviceAccountJsonPath != null && !serviceAccountJsonPath.isEmpty()) {
            credentialsStream = new FileInputStream(serviceAccountJsonPath);
            log.info("Using Google credentials from file: {}", serviceAccountJsonPath);
        }

        if (credentialsStream == null) {
            log.warn("No Google Play credentials configured");
            return null;
        }

        return ServiceAccountCredentials.fromStream(credentialsStream);
    }

    // ========== Enums and DTOs ==========

    public enum PurchaseType {
        SUBSCRIPTION,
        ONE_TIME
    }

    @Data
    @lombok.Builder
    public static class GooglePlayValidationResult {
        private boolean valid;
        private String errorMessage;
        private String productId;
        private String purchaseToken;
        private String orderId;
        private LocalDateTime startTime;
        private LocalDateTime expiryTime;
        private boolean isExpired;
        private boolean isCancelled;
        private Integer cancelReason;
        private boolean isAutoRenewing;
        private Integer paymentState;
        private Integer purchaseState;
        private Integer consumptionState;
        private Long priceAmountMicros;
        private String priceCurrencyCode;
        private String countryCode;
        private String regionCode;
        private String developerPayload;
        private String linkedPurchaseToken;
        private boolean isAcknowledged;
        private PurchaseType purchaseType;

        public static GooglePlayValidationResult failure(String message) {
            return GooglePlayValidationResult.builder()
                    .valid(false)
                    .errorMessage(message)
                    .build();
        }
    }
}
