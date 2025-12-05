package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity for tracking processed PayPal webhook events.
 * Used for idempotency to prevent duplicate processing of the same webhook event.
 */
@Entity
@Table(name = "processed_paypal_webhooks",
    indexes = {
        @Index(name = "idx_paypal_webhook_event_id", columnList = "eventId"),
        @Index(name = "idx_paypal_webhook_resource_id", columnList = "resourceId"),
        @Index(name = "idx_paypal_webhook_processed_at", columnList = "processedAt")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedPayPalWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * PayPal webhook event ID (unique identifier from PayPal)
     */
    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    /**
     * Event type (e.g., PAYMENT.CAPTURE.COMPLETED, CHECKOUT.ORDER.APPROVED)
     */
    @Column(nullable = false, length = 100)
    private String eventType;

    /**
     * Resource ID (order ID, capture ID, subscription ID, etc.)
     */
    @Column(length = 100)
    private String resourceId;

    /**
     * Resource type (order, capture, subscription, etc.)
     */
    @Column(length = 50)
    private String resourceType;

    /**
     * Associated internal payment ID (if applicable)
     */
    private Integer paymentId;

    /**
     * When this webhook was processed
     */
    @Column(nullable = false)
    private LocalDateTime processedAt;

    /**
     * Processing result
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * Error message if processing failed
     */
    @Column(length = 1000)
    private String errorMessage;

    /**
     * Raw webhook payload for debugging
     */
    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PROCESSING";
        }
    }

    public void markSuccess() {
        this.status = STATUS_SUCCESS;
    }

    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.errorMessage = errorMessage;
    }

    public void markSkipped() {
        this.status = STATUS_SKIPPED;
    }

    public boolean isSuccessful() {
        return STATUS_SUCCESS.equals(this.status);
    }
}
