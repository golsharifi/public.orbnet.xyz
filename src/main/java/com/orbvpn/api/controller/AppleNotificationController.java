package com.orbvpn.api.controller;

import com.orbvpn.api.service.subscription.notification.AppleNotificationProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.orbvpn.api.domain.dto.AppleNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AppleNotificationController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppleNotificationProcessor appleNotificationProcessor;

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

            // Decode the signed payload (simplified format)
            String decodedPayload = decodeSignedPayload(signedPayload);

            // Parse the decoded payload into AppleNotification
            AppleNotification notification = objectMapper.readValue(decodedPayload, AppleNotification.class);

            // Process the notification
            appleNotificationProcessor.processNotification(notification);

            return ResponseEntity.ok("Notification processed successfully");

        } catch (Exception e) {
            log.error("Error processing Apple notification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing notification: " + e.getMessage());
        }
    }

    private String decodeSignedPayload(String signedPayload) throws Exception {
        // Split the JWT token
        String[] parts = signedPayload.split("\\.");
        if (parts.length != 3) {
            log.error("Invalid JWT format: expected 3 parts, got {}", parts.length);
            throw new IllegalArgumentException("Invalid signed payload format");
        }

        // Decode and return only the payload part
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }
}