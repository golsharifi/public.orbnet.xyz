package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking processed Yandex Pay webhook events.
 * Used for idempotency to prevent duplicate processing of the same webhook event.
 */
@Entity
@Table(name = "processed_yandexpay_webhooks",
    indexes = {
        @Index(name = "idx_yandexpay_webhook_order_id", columnList = "orderId"),
        @Index(name = "idx_yandexpay_webhook_event_status", columnList = "event,status"),
        @Index(name = "idx_yandexpay_webhook_processed_at", columnList = "processedAt")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_yandexpay_order_event_status",
                columnNames = {"orderId", "event", "paymentStatus"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedYandexPayWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Order ID from our system (ORB-YP-xxx)
     */
    @Column(nullable = false, length = 100)
    private String orderId;

    /**
     * Yandex Pay order ID (from Yandex)
     */
    @Column(length = 100)
    private String yandexOrderId;

    /**
     * Event type (ORDER_PAID, ORDER_CAPTURED, ORDER_FAILED, etc.)
     */
    @Column(nullable = false, length = 50)
    private String event;

    /**
     * Payment status from webhook
     */
    @Column(length = 50)
    private String paymentStatus;

    /**
     * Operation ID from Yandex Pay
     */
    @Column(length = 100)
    private String operationId;

    /**
     * Amount from webhook
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Currency from webhook
     */
    @Column(length = 10)
    private String currency;

    /**
     * Associated internal payment ID
     */
    private Integer internalPaymentId;

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
    public static final String STATUS_DUPLICATE = "DUPLICATE";

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

    public void markDuplicate() {
        this.status = STATUS_DUPLICATE;
    }

    public boolean isSuccessful() {
        return STATUS_SUCCESS.equals(this.status);
    }
}
