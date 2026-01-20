package com.orbvpn.api.controller;

import com.orbvpn.api.service.subscription.notification.AppleNotificationProcessor;
import com.orbvpn.api.service.subscription.AppleJwtVerificationService;
import com.orbvpn.api.config.AppleConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.orbvpn.api.domain.dto.AppleNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AppleNotificationController {

    private final ObjectMapper objectMapper;
    private final AppleNotificationProcessor appleNotificationProcessor;
    private final AppleJwtVerificationService appleJwtVerificationService;
    private final AppleConfiguration appleConfiguration;

    @PostMapping("/apple/notifications")
    public ResponseEntity<String> handleAppleNotification(@RequestBody String rawBody) {
        log.info("Apple notification endpoint invoked");

        try {
            // Parse the raw JSON first
            JsonNode rootNode = objectMapper.readTree(rawBody);
            String signedPayload = rootNode.path("signedPayload").asText();

            if (signedPayload == null || signedPayload.isEmpty()) {
                log.error("Missing signedPayload in notification");
                return ResponseEntity.badRequest().body("Missing signedPayload");
            }

            // Verify JWT signature and decode the payload
            AppleNotification notification;
            try {
                notification = appleJwtVerificationService.verifyAndDecodeNotification(signedPayload);
            } catch (SecurityException e) {
                log.error("JWT verification failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("JWT signature verification failed");
            }

            // Validate bundle ID
            String expectedBundleId = appleConfiguration.getBundleId();
            if (expectedBundleId != null && !expectedBundleId.isEmpty()) {
                String notificationBundleId = notification.getBundleId();
                if (notificationBundleId != null && !notificationBundleId.equals(expectedBundleId)) {
                    log.error("Bundle ID mismatch: expected {}, got {}", expectedBundleId, notificationBundleId);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("Invalid bundle ID");
                }
            }

            // Validate environment (production vs sandbox)
            String expectedEnvironment = appleConfiguration.getEnvironment();
            if (expectedEnvironment != null && notification.getData() != null) {
                String notificationEnvironment = notification.getData().getEnvironment();
                if (notificationEnvironment != null && !notificationEnvironment.equalsIgnoreCase(expectedEnvironment)) {
                    log.warn("Environment mismatch: expected {}, got {} - processing anyway",
                            expectedEnvironment, notificationEnvironment);
                    // Don't reject, just log warning - sandbox testing should still work
                }
            }

            // Process the notification
            appleNotificationProcessor.processNotification(notification);

            return ResponseEntity.ok("Notification processed successfully");

        } catch (Exception e) {
            log.error("Error processing Apple notification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing notification: " + e.getMessage());
        }
    }
}