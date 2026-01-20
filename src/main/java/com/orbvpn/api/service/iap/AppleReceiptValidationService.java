package com.orbvpn.api.service.iap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for validating Apple App Store receipts.
 *
 * Apple Receipt Validation Flow:
 * 1. Receive receipt from client
 * 2. Send to Apple's verifyReceipt endpoint
 * 3. Parse response to get transaction details
 * 4. Return validated purchase info
 *
 * Documentation: https://developer.apple.com/documentation/storekit/in-app_purchase/validating_receipts_with_the_app_store
 */
@Service
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Lazy
public class AppleReceiptValidationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${apple.iap.shared-secret:}")
    private String sharedSecret;

    @Value("${apple.iap.bundle-id:com.orbvpn.app}")
    private String bundleId;

    @Value("${apple.iap.production-url:https://buy.itunes.apple.com/verifyReceipt}")
    private String productionUrl;

    @Value("${apple.iap.sandbox-url:https://sandbox.itunes.apple.com/verifyReceipt}")
    private String sandboxUrl;

    /**
     * Validate an Apple receipt and return the purchase information.
     *
     * @param receiptData Base64 encoded receipt data from the client
     * @param expectedProductId The product ID that should be in the receipt
     * @param transactionId The transaction ID to verify
     * @return Validated receipt result
     */
    public AppleReceiptValidationResult validateReceipt(String receiptData, String expectedProductId, String transactionId) {
        log.info("Validating Apple receipt for product: {}, transaction: {}", expectedProductId, transactionId);

        if (sharedSecret == null || sharedSecret.isEmpty()) {
            log.error("Apple shared secret not configured");
            return AppleReceiptValidationResult.failure("Apple IAP not configured");
        }

        try {
            // First try production endpoint
            AppleVerifyReceiptResponse response = sendVerifyRequest(receiptData, productionUrl);

            // If status is 21007, receipt is from sandbox - retry with sandbox URL
            if (response.getStatus() == 21007) {
                log.info("Receipt is from sandbox, retrying with sandbox URL");
                response = sendVerifyRequest(receiptData, sandboxUrl);
            }

            // Check for errors
            if (response.getStatus() != 0) {
                String errorMessage = getErrorMessage(response.getStatus());
                log.error("Apple receipt validation failed with status {}: {}", response.getStatus(), errorMessage);
                return AppleReceiptValidationResult.failure(errorMessage);
            }

            // Validate bundle ID
            if (response.getReceipt() != null &&
                !bundleId.equals(response.getReceipt().getBundleId())) {
                log.error("Bundle ID mismatch. Expected: {}, Got: {}",
                        bundleId, response.getReceipt().getBundleId());
                return AppleReceiptValidationResult.failure("Invalid app bundle");
            }

            // Find the specific transaction
            AppleInAppPurchase purchase = findTransaction(response, transactionId, expectedProductId);
            if (purchase == null) {
                log.error("Transaction not found in receipt: {}", transactionId);
                return AppleReceiptValidationResult.failure("Transaction not found in receipt");
            }

            // For subscriptions, check latest receipt info
            AppleInAppPurchase latestPurchase = purchase;
            if (response.getLatestReceiptInfo() != null && !response.getLatestReceiptInfo().isEmpty()) {
                // Find the latest transaction for this product
                latestPurchase = response.getLatestReceiptInfo().stream()
                        .filter(p -> expectedProductId.equals(p.getProductId()))
                        .max(Comparator.comparing(p -> Long.parseLong(p.getExpiresDateMs() != null ? p.getExpiresDateMs() : "0")))
                        .orElse(purchase);
            }

            // Check if subscription is still valid
            boolean isExpired = false;
            LocalDateTime expiresAt = null;
            if (latestPurchase.getExpiresDateMs() != null) {
                long expiresMs = Long.parseLong(latestPurchase.getExpiresDateMs());
                expiresAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresMs), ZoneId.systemDefault());
                isExpired = expiresMs < System.currentTimeMillis();
            }

            // Check for cancellation
            if (latestPurchase.getCancellationDate() != null) {
                log.warn("Purchase was cancelled: {}", transactionId);
                return AppleReceiptValidationResult.failure("Purchase was cancelled");
            }

            log.info("Apple receipt validated successfully for product: {}", expectedProductId);

            return AppleReceiptValidationResult.builder()
                    .valid(true)
                    .productId(latestPurchase.getProductId())
                    .transactionId(latestPurchase.getTransactionId())
                    .originalTransactionId(latestPurchase.getOriginalTransactionId())
                    .purchaseDate(parseDate(latestPurchase.getPurchaseDateMs()))
                    .expiresDate(expiresAt)
                    .isExpired(isExpired)
                    .isTrial(Boolean.parseBoolean(latestPurchase.getIsTrialPeriod()))
                    .isIntroOffer(Boolean.parseBoolean(latestPurchase.getIsInIntroOfferPeriod()))
                    .environment(response.getEnvironment())
                    .latestReceipt(response.getLatestReceipt())
                    .build();

        } catch (Exception e) {
            log.error("Error validating Apple receipt: {}", e.getMessage(), e);
            return AppleReceiptValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Send verification request to Apple.
     */
    private AppleVerifyReceiptResponse sendVerifyRequest(String receiptData, String url) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("receipt-data", receiptData);
        requestBody.put("password", sharedSecret);
        requestBody.put("exclude-old-transactions", true);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        ResponseEntity<AppleVerifyReceiptResponse> response = restTemplate.postForEntity(
                url, request, AppleVerifyReceiptResponse.class);

        return response.getBody();
    }

    /**
     * Find a specific transaction in the receipt.
     */
    private AppleInAppPurchase findTransaction(AppleVerifyReceiptResponse response,
                                                String transactionId, String productId) {
        // Check latest_receipt_info first (for subscriptions)
        if (response.getLatestReceiptInfo() != null) {
            for (AppleInAppPurchase purchase : response.getLatestReceiptInfo()) {
                if (transactionId.equals(purchase.getTransactionId()) ||
                    transactionId.equals(purchase.getOriginalTransactionId())) {
                    return purchase;
                }
            }
        }

        // Check receipt.in_app
        if (response.getReceipt() != null && response.getReceipt().getInApp() != null) {
            for (AppleInAppPurchase purchase : response.getReceipt().getInApp()) {
                if (transactionId.equals(purchase.getTransactionId()) ||
                    transactionId.equals(purchase.getOriginalTransactionId())) {
                    return purchase;
                }
            }
        }

        // If transaction not found by ID, try to find by product ID
        if (response.getLatestReceiptInfo() != null) {
            return response.getLatestReceiptInfo().stream()
                    .filter(p -> productId.equals(p.getProductId()))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    /**
     * Parse milliseconds timestamp to LocalDateTime.
     */
    private LocalDateTime parseDate(String milliseconds) {
        if (milliseconds == null) return null;
        try {
            long ms = Long.parseLong(milliseconds);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get human-readable error message for Apple status codes.
     */
    private String getErrorMessage(int status) {
        return switch (status) {
            case 21000 -> "The App Store could not read the receipt";
            case 21002 -> "The receipt data is malformed";
            case 21003 -> "The receipt could not be authenticated";
            case 21004 -> "The shared secret does not match";
            case 21005 -> "The receipt server is not available";
            case 21006 -> "The receipt is valid but the subscription is expired";
            case 21007 -> "Receipt is from sandbox but sent to production";
            case 21008 -> "Receipt is from production but sent to sandbox";
            case 21009 -> "Internal server error";
            case 21010 -> "The user account cannot be found or has been deleted";
            default -> "Unknown error: " + status;
        };
    }

    // ========== Response DTOs ==========

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppleVerifyReceiptResponse {
        private int status;
        private String environment;
        private AppleReceipt receipt;

        @JsonProperty("latest_receipt")
        private String latestReceipt;

        @JsonProperty("latest_receipt_info")
        private List<AppleInAppPurchase> latestReceiptInfo;

        @JsonProperty("pending_renewal_info")
        private List<ApplePendingRenewal> pendingRenewalInfo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppleReceipt {
        @JsonProperty("bundle_id")
        private String bundleId;

        @JsonProperty("application_version")
        private String applicationVersion;

        @JsonProperty("in_app")
        private List<AppleInAppPurchase> inApp;

        @JsonProperty("receipt_type")
        private String receiptType;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppleInAppPurchase {
        @JsonProperty("product_id")
        private String productId;

        @JsonProperty("transaction_id")
        private String transactionId;

        @JsonProperty("original_transaction_id")
        private String originalTransactionId;

        @JsonProperty("purchase_date_ms")
        private String purchaseDateMs;

        @JsonProperty("expires_date_ms")
        private String expiresDateMs;

        @JsonProperty("cancellation_date")
        private String cancellationDate;

        @JsonProperty("is_trial_period")
        private String isTrialPeriod;

        @JsonProperty("is_in_intro_offer_period")
        private String isInIntroOfferPeriod;

        private String quantity;

        @JsonProperty("web_order_line_item_id")
        private String webOrderLineItemId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplePendingRenewal {
        @JsonProperty("product_id")
        private String productId;

        @JsonProperty("auto_renew_product_id")
        private String autoRenewProductId;

        @JsonProperty("auto_renew_status")
        private String autoRenewStatus;

        @JsonProperty("expiration_intent")
        private String expirationIntent;

        @JsonProperty("is_in_billing_retry_period")
        private String isInBillingRetryPeriod;
    }

    // ========== Result DTO ==========

    @Data
    @lombok.Builder
    public static class AppleReceiptValidationResult {
        private boolean valid;
        private String errorMessage;
        private String productId;
        private String transactionId;
        private String originalTransactionId;
        private LocalDateTime purchaseDate;
        private LocalDateTime expiresDate;
        private boolean isExpired;
        private boolean isTrial;
        private boolean isIntroOffer;
        private String environment;
        private String latestReceipt;

        public static AppleReceiptValidationResult failure(String message) {
            return AppleReceiptValidationResult.builder()
                    .valid(false)
                    .errorMessage(message)
                    .build();
        }
    }
}
