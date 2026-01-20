package com.orbvpn.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.service.subscription.notification.GooglePlayNotificationProcessor;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
public class GooglePlayNotificationController {
    private final GooglePlayNotificationProcessor notificationProcessor;
    private final ObjectMapper objectMapper;

    @PostMapping("/google/notifications")
    public ResponseEntity<Void> handleGooglePlayNotifications(@RequestBody String rawRequestBody) {
        log.info("Received Google Play notification");

        try {
            // Parse the raw JSON
            Map<String, Object> requestBodyMap = objectMapper.readValue(
                    rawRequestBody,
                    new TypeReference<Map<String, Object>>() {
                    });

            // Extract and decode the "data" field
            Map<?, ?> messageMap = (Map<?, ?>) requestBodyMap.get("message");
            if (messageMap == null || messageMap.get("data") == null) {
                log.error("Invalid notification format: missing message or data");
                return ResponseEntity.badRequest().build();
            }

            // Extract messageId for deduplication (unique identifier from Google Pub/Sub)
            String messageId = (String) messageMap.get("messageId");
            if (messageId == null || messageId.isEmpty()) {
                // Generate a fallback ID from the data hash
                messageId = "fallback-" + System.currentTimeMillis();
                log.warn("Missing messageId in notification, using fallback: {}", messageId);
            }

            String base64Data = (String) messageMap.get("data");
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            String decodedData = new String(decodedBytes);

            // Deserialize the decoded data into GoogleNotification
            GoogleNotification notification = objectMapper.readValue(decodedData, GoogleNotification.class);

            // Process the notification with messageId for deduplication
            notificationProcessor.processNotification(notification, messageId);

            log.info("Successfully processed Google Play notification messageId: {}", messageId);
            return ResponseEntity.ok().build();

        } catch (IllegalStateException e) {
            // Duplicate notification - return 200 to acknowledge
            log.info("Duplicate notification, acknowledging: {}", e.getMessage());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Log the error and return proper error status
            // For transient errors, return 500 so Google retries
            // For permanent errors (malformed data), return 200 to acknowledge
            log.error("Error processing Google Play notification: {}", e.getMessage(), e);

            if (e instanceof IllegalArgumentException) {
                // Bad data - acknowledge to stop retries
                return ResponseEntity.ok().build();
            }

            // Return 500 for transient errors so Google retries
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}