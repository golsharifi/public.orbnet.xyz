package com.orbvpn.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.service.subscription.notification.GooglePlayNotificationProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GooglePlayNotificationControllerTest {

    @Mock
    private GooglePlayNotificationProcessor notificationProcessor;

    private ObjectMapper objectMapper;
    private GooglePlayNotificationController controller;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new GooglePlayNotificationController(notificationProcessor, objectMapper);
    }

    @Test
    void handleGooglePlayNotifications_WithValidRequest_ReturnsOk() throws Exception {
        String validRequestBody = createValidPubSubMessage();

        doNothing().when(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(validRequestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());
    }

    @Test
    void handleGooglePlayNotifications_WithDuplicateMessage_ReturnsOk() throws Exception {
        String validRequestBody = createValidPubSubMessage();

        doThrow(new IllegalStateException("Duplicate notification"))
                .when(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(validRequestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode()); // Still returns OK to acknowledge
    }

    @Test
    void handleGooglePlayNotifications_WithBadData_ReturnsOk() throws Exception {
        String validRequestBody = createValidPubSubMessage();

        doThrow(new IllegalArgumentException("Missing required fields"))
                .when(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(validRequestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode()); // Returns OK to stop retries for bad data
    }

    @Test
    void handleGooglePlayNotifications_WithTransientError_ReturnsInternalServerError() throws Exception {
        String validRequestBody = createValidPubSubMessage();

        doThrow(new RuntimeException("Database connection failed"))
                .when(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(validRequestBody);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()); // Returns 500 so Google retries
    }

    @Test
    void handleGooglePlayNotifications_WithMissingMessageField_ReturnsBadRequest() throws Exception {
        String invalidRequestBody = "{\"invalid\": \"payload\"}";

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(invalidRequestBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(notificationProcessor, never()).processNotification(any(), anyString());
    }

    @Test
    void handleGooglePlayNotifications_WithMissingDataField_ReturnsBadRequest() throws Exception {
        String missingDataBody = "{\"message\": {\"messageId\": \"123\"}}";

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(missingDataBody);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void handleGooglePlayNotifications_WithMissingMessageId_GeneratesFallbackId() throws Exception {
        // Message without messageId
        String data = java.util.Base64.getEncoder().encodeToString(
                "{\"subscriptionNotification\":{\"purchaseToken\":\"token\",\"subscriptionId\":\"sub\",\"notificationType\":4}}".getBytes());
        String requestBody = "{\"message\": {\"data\": \"" + data + "\"}}";

        doNothing().when(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Verify that a fallback messageId was generated (starts with "fallback-")
        verify(notificationProcessor).processNotification(any(GoogleNotification.class), startsWith("fallback-"));
    }

    @Test
    void handleGooglePlayNotifications_ExtractsMessageIdFromPubSub() throws Exception {
        String data = java.util.Base64.getEncoder().encodeToString(
                "{\"subscriptionNotification\":{\"purchaseToken\":\"token\",\"subscriptionId\":\"sub\",\"notificationType\":4}}".getBytes());
        String expectedMessageId = "unique-pubsub-message-id";
        String requestBody = "{\"message\": {\"messageId\": \"" + expectedMessageId + "\", \"data\": \"" + data + "\"}}";

        doNothing().when(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationProcessor).processNotification(any(GoogleNotification.class), eq(expectedMessageId));
    }

    @Test
    void handleGooglePlayNotifications_DecodesBase64Data() throws Exception {
        // Create a proper base64 encoded Google notification
        String notificationJson = "{\"subscriptionNotification\":{\"purchaseToken\":\"test-token\",\"subscriptionId\":\"monthly_sub\",\"notificationType\":4}}";
        String base64Data = java.util.Base64.getEncoder().encodeToString(notificationJson.getBytes());
        String requestBody = "{\"message\": {\"messageId\": \"test-123\", \"data\": \"" + base64Data + "\"}}";

        doNothing().when(notificationProcessor).processNotification(any(GoogleNotification.class), anyString());

        ResponseEntity<Void> response = controller.handleGooglePlayNotifications(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationProcessor).processNotification(argThat(notification ->
            notification.getSubscriptionNotification() != null &&
            "test-token".equals(notification.getSubscriptionNotification().getPurchaseToken()) &&
            "monthly_sub".equals(notification.getSubscriptionNotification().getSubscriptionId()) &&
            notification.getSubscriptionNotification().getNotificationType() == 4
        ), eq("test-123"));
    }

    private String createValidPubSubMessage() {
        String notificationJson = "{\"subscriptionNotification\":{\"purchaseToken\":\"test-token\",\"subscriptionId\":\"monthly_sub\",\"notificationType\":4}}";
        String base64Data = java.util.Base64.getEncoder().encodeToString(notificationJson.getBytes());
        return "{\"message\": {\"messageId\": \"test-message-id\", \"data\": \"" + base64Data + "\"}}";
    }
}
