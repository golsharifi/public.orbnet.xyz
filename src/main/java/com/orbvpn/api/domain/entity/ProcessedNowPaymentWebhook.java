package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking processed NOWPayments webhook events.
 * Used for idempotency to prevent duplicate processing of the same IPN callback.
 */
@Entity
@Table(name = "processed_nowpayment_webhooks",
    indexes = {
        @Index(name = "idx_nowpayment_webhook_payment_id", columnList = "paymentId"),
        @Index(name = "idx_nowpayment_webhook_order_id", columnList = "orderId"),
        @Index(name = "idx_nowpayment_webhook_processed_at", columnList = "processedAt"),
        @Index(name = "idx_nowpayment_webhook_status_payment", columnList = "paymentStatus,paymentId")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedNowPaymentWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NOWPayments payment ID (unique identifier from NOWPayments)
     */
    @Column(nullable = false, length = 100)
    private String paymentId;

    /**
     * Our internal order ID
     */
    @Column(length = 100)
    private String orderId;

    /**
     * Payment status from the IPN callback
     */
    @Column(nullable = false, length = 50)
    private String paymentStatus;

    /**
     * Amount actually paid
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal actuallyPaid;

    /**
     * Currency paid
     */
    @Column(length = 20)
    private String payCurrency;

    /**
     * Associated internal payment ID (if applicable)
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

    public boolean isDuplicate() {
        return STATUS_DUPLICATE.equals(this.status);
    }
}
