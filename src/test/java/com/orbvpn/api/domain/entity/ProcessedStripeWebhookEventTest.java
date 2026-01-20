package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedStripeWebhookEventTest {

    @Test
    void constructor_SetsEventIdAndDefaults() {
        String eventId = "evt_test123";
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent(eventId);

        assertEquals(eventId, event.getEventId());
        assertEquals("PROCESSING", event.getStatus());
        assertNotNull(event.getProcessedAt());
        assertTrue(event.getProcessedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void markSuccess_SetsStatusToSuccess() {
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent("evt_test123");

        event.markSuccess();

        assertEquals("SUCCESS", event.getStatus());
    }

    @Test
    void markFailed_SetsStatusAndErrorMessage() {
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent("evt_test123");
        String errorMessage = "Test error message";

        event.markFailed(errorMessage);

        assertEquals("FAILED", event.getStatus());
        assertEquals(errorMessage, event.getErrorMessage());
    }

    @Test
    void markFailed_TruncatesLongErrorMessage() {
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent("evt_test123");
        String longErrorMessage = "A".repeat(600); // 600 characters

        event.markFailed(longErrorMessage);

        assertEquals("FAILED", event.getStatus());
        assertEquals(500, event.getErrorMessage().length());
    }

    @Test
    void markFailed_HandlesNullErrorMessage() {
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent("evt_test123");

        event.markFailed(null);

        assertEquals("FAILED", event.getStatus());
        assertNull(event.getErrorMessage());
    }

    @Test
    void markSkipped_SetsStatusToSkipped() {
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent("evt_test123");

        event.markSkipped();

        assertEquals("SKIPPED", event.getStatus());
    }

    @Test
    void setters_WorkCorrectly() {
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent("evt_test123");

        event.setEventType("customer.subscription.created");
        event.setSubscriptionId("sub_test123");
        event.setCustomerId("cus_test123");
        event.setPaymentIntentId("pi_test123");

        assertEquals("customer.subscription.created", event.getEventType());
        assertEquals("sub_test123", event.getSubscriptionId());
        assertEquals("cus_test123", event.getCustomerId());
        assertEquals("pi_test123", event.getPaymentIntentId());
    }

    @Test
    void noArgsConstructor_CreatesEmptyObject() {
        ProcessedStripeWebhookEvent event = new ProcessedStripeWebhookEvent();

        assertNull(event.getEventId());
        assertNull(event.getStatus());
        assertNull(event.getProcessedAt());
    }
}
