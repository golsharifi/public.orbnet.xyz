package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity for tracking processed Stripe webhook events.
 * Used to implement idempotency and prevent duplicate processing.
 * Stripe webhooks can be delivered multiple times, so we track each event ID.
 */
@Entity
@Table(name = "processed_stripe_webhook_event", indexes = {
        @Index(name = "idx_processed_stripe_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_processed_stripe_processed_at", columnList = "processed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessedStripeWebhookEvent {

    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "subscription_id", length = 128)
    private String subscriptionId;

    @Column(name = "customer_id", length = 128)
    private String customerId;

    @Column(name = "payment_intent_id", length = 128)
    private String paymentIntentId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public ProcessedStripeWebhookEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = LocalDateTime.now();
        this.status = "PROCESSING";
    }

    public void markSuccess() {
        this.status = "SUCCESS";
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
    }

    public void markSkipped() {
        this.status = "SKIPPED";
    }
}
