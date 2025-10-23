package com.orbvpn.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.service.subscription.notification.GooglePlayNotificationProcessor;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
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
                return ResponseEntity.ok().build();
            }

            String base64Data = (String) messageMap.get("data");
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            String decodedData = new String(decodedBytes);

            // Deserialize the decoded data into GoogleNotification
            GoogleNotification notification = objectMapper.readValue(decodedData, GoogleNotification.class);

            // Process the notification
            notificationProcessor.processNotification(notification);

            log.info("Successfully processed Google Play notification");
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            // Log the error but return 200 OK to prevent Google from retrying
            log.error("Error processing Google Play notification: {}", e.getMessage(), e);
            return ResponseEntity.ok().build();
        }
    }
}