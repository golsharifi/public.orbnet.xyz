package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedAppleNotificationTest {

    @Test
    void constructor_SetsDefaultValues() {
        ProcessedAppleNotification notification = new ProcessedAppleNotification("test-uuid");

        assertEquals("test-uuid", notification.getNotificationUUID());
        assertEquals("PROCESSING", notification.getStatus());
        assertNotNull(notification.getProcessedAt());
    }

    @Test
    void markSuccess_SetsStatusToSuccess() {
        ProcessedAppleNotification notification = new ProcessedAppleNotification("test-uuid");

        notification.markSuccess();

        assertEquals("SUCCESS", notification.getStatus());
    }

    @Test
    void markFailed_SetsStatusAndErrorMessage() {
        ProcessedAppleNotification notification = new ProcessedAppleNotification("test-uuid");

        notification.markFailed("Test error message");

        assertEquals("FAILED", notification.getStatus());
        assertEquals("Test error message", notification.getErrorMessage());
    }

    @Test
    void markFailed_TruncatesLongErrorMessage() {
        ProcessedAppleNotification notification = new ProcessedAppleNotification("test-uuid");

        // Create a message longer than 500 characters
        String longMessage = "x".repeat(600);
        notification.markFailed(longMessage);

        assertEquals("FAILED", notification.getStatus());
        assertEquals(500, notification.getErrorMessage().length());
    }

    @Test
    void markFailed_HandlesNullErrorMessage() {
        ProcessedAppleNotification notification = new ProcessedAppleNotification("test-uuid");

        notification.markFailed(null);

        assertEquals("FAILED", notification.getStatus());
        assertNull(notification.getErrorMessage());
    }

    @Test
    void setNotificationType_StoresValue() {
        ProcessedAppleNotification notification = new ProcessedAppleNotification("test-uuid");

        notification.setNotificationType("DID_RENEW");

        assertEquals("DID_RENEW", notification.getNotificationType());
    }

    @Test
    void setOriginalTransactionId_StoresValue() {
        ProcessedAppleNotification notification = new ProcessedAppleNotification("test-uuid");

        notification.setOriginalTransactionId("orig-tx-123");

        assertEquals("orig-tx-123", notification.getOriginalTransactionId());
    }
}
