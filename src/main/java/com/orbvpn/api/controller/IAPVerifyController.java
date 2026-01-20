package com.orbvpn.api.controller;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.AppleService;
import com.orbvpn.api.service.subscription.GooglePlayService;
import com.orbvpn.api.domain.dto.AppleSubscriptionData;
import com.orbvpn.api.domain.dto.GooglePlaySubscriptionInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for IAP receipt verification
 * This endpoint matches the Go backend's /api/v1/payments/verify-receipt
 * to support the Flutter app.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/payments")
public class IAPVerifyController {

    private final AppleService appleService;
    private final GooglePlayService googlePlayService;
    private final UserService userService;

    // Google Play package name for the app
    private static final String GOOGLE_PLAY_PACKAGE = "com.orbvpn.android";

    @PostMapping("/verify-receipt")
    public ResponseEntity<?> verifyReceipt(
            @RequestBody VerifyReceiptRequest request,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceId,
            Authentication authentication) {

        log.info("Verify receipt request: platform={}, productId={}",
            request.getPlatform(), request.getProductId());

        try {
            User user = userService.getUserFromAuthentication(authentication);
            if (user == null) {
                return ResponseEntity.status(401).body(
                    createErrorResponse("Not authenticated", "AUTH_ERROR"));
            }

            if (request.getPlatform() == null || request.getReceiptData() == null) {
                return ResponseEntity.badRequest().body(
                    createErrorResponse("Missing required fields", "INVALID_REQUEST"));
            }

            String platform = request.getPlatform().toLowerCase();

            switch (platform) {
                case "ios":
                    return verifyAppleReceipt(request, deviceId, user);
                case "android":
                    return verifyGooglePlayReceipt(request, deviceId, user);
                default:
                    return ResponseEntity.badRequest().body(
                        createErrorResponse("Unsupported platform: " + platform, "UNSUPPORTED_PLATFORM"));
            }
        } catch (Exception e) {
            log.error("Error verifying receipt", e);
            return ResponseEntity.badRequest().body(
                createErrorResponse("Verification failed: " + e.getMessage(), "VERIFICATION_FAILED"));
        }
    }

    private ResponseEntity<?> verifyAppleReceipt(VerifyReceiptRequest request, String deviceId, User user) {
        try {
            AppleSubscriptionData data = appleService.getSubscriptionData(
                request.getReceiptData(),
                deviceId != null ? deviceId : "unknown",
                user);

            boolean isActive = data != null && data.getExpiresAt() != null &&
                data.getExpiresAt().isAfter(LocalDateTime.now());

            VerifyReceiptResponse response = new VerifyReceiptResponse();
            response.setValid(isActive);
            response.setProductId(request.getProductId());
            response.setTransactionId(data != null ? data.getOriginalTransactionId() : null);
            response.setPurchaseDate(data != null ? data.getExpiresAt() : null);
            response.setMessage(isActive ? "Receipt verified successfully" : "Subscription not active");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Apple receipt verification failed", e);
            VerifyReceiptResponse response = new VerifyReceiptResponse();
            response.setValid(false);
            response.setProductId(request.getProductId());
            response.setMessage("Apple verification failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    private ResponseEntity<?> verifyGooglePlayReceipt(VerifyReceiptRequest request, String deviceId, User user) {
        try {
            // For Google Play, the receiptData is the purchase token
            GooglePlaySubscriptionInfo info = googlePlayService.verifyTokenWithGooglePlay(
                GOOGLE_PLAY_PACKAGE,
                request.getReceiptData(),
                request.getProductId(),
                deviceId != null ? deviceId : "unknown",
                user,
                null);

            boolean isActive = info != null && info.getExpiresAt() != null &&
                info.getExpiresAt().isAfter(LocalDateTime.now());

            VerifyReceiptResponse response = new VerifyReceiptResponse();
            response.setValid(isActive);
            response.setProductId(request.getProductId());
            response.setTransactionId(info != null ? info.getOrderId() : null);
            response.setPurchaseDate(info != null ? info.getExpiresAt() : null);
            response.setMessage(isActive ?
                "Receipt verified successfully" : "Subscription not active or expired");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Google Play verification failed", e);
            VerifyReceiptResponse response = new VerifyReceiptResponse();
            response.setValid(false);
            response.setProductId(request.getProductId());
            response.setMessage("Google Play verification failed: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    private Map<String, Object> createErrorResponse(String message, String code) {
        Map<String, Object> error = new HashMap<>();
        error.put("valid", false);
        error.put("message", message);
        error.put("code", code);
        return error;
    }

    @Data
    public static class VerifyReceiptRequest {
        private String platform;

        @JsonProperty("receipt_data")
        private String receiptData;

        @JsonProperty("product_id")
        private String productId;
    }

    @Data
    public static class VerifyReceiptResponse {
        private boolean valid;

        @JsonProperty("product_id")
        private String productId;

        @JsonProperty("transaction_id")
        private String transactionId;

        @JsonProperty("purchase_date")
        private LocalDateTime purchaseDate;

        private String message;
    }
}
