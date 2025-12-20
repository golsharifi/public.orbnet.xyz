package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedGoogleNotificationTest {

    @Test
    void constructor_SetsMessageIdAndDefaults() {
        String messageId = "test-message-id";
        ProcessedGoogleNotification notification = new ProcessedGoogleNotification(messageId);

        assertEquals(messageId, notification.getMessageId());
        assertEquals("PROCESSING", notification.getStatus());
        assertNotNull(notification.getProcessedAt());
        assertTrue(notification.getProcessedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void markSuccess_SetsStatusToSuccess() {
        ProcessedGoogleNotification notification = new ProcessedGoogleNotification("test-id");

        notification.markSuccess();

        assertEquals("SUCCESS", notification.getStatus());
    }

    @Test
    void markFailed_SetsStatusAndErrorMessage() {
        ProcessedGoogleNotification notification = new ProcessedGoogleNotification("test-id");
        String errorMessage = "Test error message";

        notification.markFailed(errorMessage);

        assertEquals("FAILED", notification.getStatus());
        assertEquals(errorMessage, notification.getErrorMessage());
    }

    @Test
    void markFailed_TruncatesLongErrorMessage() {
        ProcessedGoogleNotification notification = new ProcessedGoogleNotification("test-id");
        String longErrorMessage = "A".repeat(600); // 600 characters

        notification.markFailed(longErrorMessage);

        assertEquals("FAILED", notification.getStatus());
        assertEquals(500, notification.getErrorMessage().length());
    }

    @Test
    void markFailed_HandlesNullErrorMessage() {
        ProcessedGoogleNotification notification = new ProcessedGoogleNotification("test-id");

        notification.markFailed(null);

        assertEquals("FAILED", notification.getStatus());
        assertNull(notification.getErrorMessage());
    }

    @Test
    void setters_WorkCorrectly() {
        ProcessedGoogleNotification notification = new ProcessedGoogleNotification("test-id");

        notification.setNotificationType(4);
        notification.setPurchaseToken("purchase-token-123");
        notification.setSubscriptionId("monthly_sub");

        assertEquals(4, notification.getNotificationType());
        assertEquals("purchase-token-123", notification.getPurchaseToken());
        assertEquals("monthly_sub", notification.getSubscriptionId());
    }

    @Test
    void noArgsConstructor_CreatesEmptyObject() {
        ProcessedGoogleNotification notification = new ProcessedGoogleNotification();

        assertNull(notification.getMessageId());
        assertNull(notification.getStatus());
        assertNull(notification.getProcessedAt());
    }
}
