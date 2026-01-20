package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedPayPalWebhookTest {

    @Test
    void builder_CreatesValidEntity() {
        ProcessedPayPalWebhook webhook = ProcessedPayPalWebhook.builder()
                .eventId("WH-123456789")
                .eventType("PAYMENT.CAPTURE.COMPLETED")
                .resourceId("5O190127TN364715T")
                .resourceType("capture")
                .paymentId(123)
                .status("SUCCESS")
                .build();

        assertEquals("WH-123456789", webhook.getEventId());
        assertEquals("PAYMENT.CAPTURE.COMPLETED", webhook.getEventType());
        assertEquals("5O190127TN364715T", webhook.getResourceId());
        assertEquals("capture", webhook.getResourceType());
        assertEquals(123, webhook.getPaymentId());
        assertEquals("SUCCESS", webhook.getStatus());
    }

    @Test
    void markSuccess_UpdatesStatus() {
        ProcessedPayPalWebhook webhook = ProcessedPayPalWebhook.builder()
                .eventId("WH-123456789")
                .eventType("PAYMENT.CAPTURE.COMPLETED")
                .build();

        webhook.markSuccess();

        assertEquals(ProcessedPayPalWebhook.STATUS_SUCCESS, webhook.getStatus());
        assertTrue(webhook.isSuccessful());
    }

    @Test
    void markFailed_UpdatesStatusAndErrorMessage() {
        ProcessedPayPalWebhook webhook = ProcessedPayPalWebhook.builder()
                .eventId("WH-123456789")
                .eventType("PAYMENT.CAPTURE.DENIED")
                .build();

        webhook.markFailed("Payment denied by bank");

        assertEquals(ProcessedPayPalWebhook.STATUS_FAILED, webhook.getStatus());
        assertEquals("Payment denied by bank", webhook.getErrorMessage());
        assertFalse(webhook.isSuccessful());
    }

    @Test
    void markSkipped_UpdatesStatus() {
        ProcessedPayPalWebhook webhook = ProcessedPayPalWebhook.builder()
                .eventId("WH-123456789")
                .eventType("CHECKOUT.ORDER.APPROVED")
                .build();

        webhook.markSkipped();

        assertEquals(ProcessedPayPalWebhook.STATUS_SKIPPED, webhook.getStatus());
        assertFalse(webhook.isSuccessful());
    }

    @Test
    void statusConstants_AreCorrect() {
        assertEquals("SUCCESS", ProcessedPayPalWebhook.STATUS_SUCCESS);
        assertEquals("FAILED", ProcessedPayPalWebhook.STATUS_FAILED);
        assertEquals("SKIPPED", ProcessedPayPalWebhook.STATUS_SKIPPED);
    }

    @Test
    void isSuccessful_ReturnsTrueOnlyForSuccess() {
        ProcessedPayPalWebhook webhook = new ProcessedPayPalWebhook();

        webhook.setStatus(ProcessedPayPalWebhook.STATUS_SUCCESS);
        assertTrue(webhook.isSuccessful());

        webhook.setStatus(ProcessedPayPalWebhook.STATUS_FAILED);
        assertFalse(webhook.isSuccessful());

        webhook.setStatus(ProcessedPayPalWebhook.STATUS_SKIPPED);
        assertFalse(webhook.isSuccessful());
    }

    @Test
    void setProcessedAt_UpdatesTimestamp() {
        ProcessedPayPalWebhook webhook = ProcessedPayPalWebhook.builder()
                .eventId("WH-123456789")
                .eventType("PAYMENT.CAPTURE.COMPLETED")
                .build();

        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        webhook.setProcessedAt(timestamp);

        assertEquals(timestamp, webhook.getProcessedAt());
    }

    @Test
    void rawPayload_CanBeSetAndRetrieved() {
        String payload = "{\"id\":\"WH-123\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}";

        ProcessedPayPalWebhook webhook = ProcessedPayPalWebhook.builder()
                .eventId("WH-123")
                .eventType("PAYMENT.CAPTURE.COMPLETED")
                .rawPayload(payload)
                .build();

        assertEquals(payload, webhook.getRawPayload());
    }

    @Test
    void defaultValues_AreNull() {
        ProcessedPayPalWebhook webhook = new ProcessedPayPalWebhook();

        assertNull(webhook.getId());
        assertNull(webhook.getEventId());
        assertNull(webhook.getEventType());
        assertNull(webhook.getResourceId());
        assertNull(webhook.getResourceType());
        assertNull(webhook.getPaymentId());
        assertNull(webhook.getProcessedAt());
        assertNull(webhook.getErrorMessage());
        assertNull(webhook.getRawPayload());
    }
}
