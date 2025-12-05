package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a Yandex Pay payment.
 * Yandex Pay is a Russian payment gateway supporting RUB payments.
 *
 * Payment statuses:
 * - PENDING: Order created, waiting for payment
 * - AUTHORIZED: Payment authorized but not captured
 * - CAPTURED: Payment successfully captured
 * - CONFIRMED: Payment confirmed and completed
 * - CANCELLED: Payment cancelled
 * - REFUNDED: Payment refunded
 * - FAILED: Payment failed
 */
@Getter
@Setter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "yandex_payment", indexes = {
        @Index(name = "idx_yandex_payment_order_id", columnList = "yandexOrderId"),
        @Index(name = "idx_yandex_payment_status", columnList = "paymentStatus"),
        @Index(name = "idx_yandex_internal_order_id", columnList = "orderId")
})
public class YandexPayment {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_AUTHORIZED = "AUTHORIZED";
    public static final String STATUS_CAPTURED = "CAPTURED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "none"))
    private User user;

    @OneToOne
    @JoinColumn(name = "payment_ref_id", foreignKey = @ForeignKey(name = "none"))
    private Payment payment;

    /**
     * Yandex Pay order ID returned from the API
     */
    @Column(length = 128)
    private String yandexOrderId;

    /**
     * Internal order ID for reference (ORB-XXXXXXXX format)
     */
    @Column(length = 64)
    private String orderId;

    /**
     * Payment status from Yandex Pay
     */
    @Column(length = 32)
    private String paymentStatus;

    /**
     * Amount in RUB (Russian Rubles)
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Currency code (RUB)
     */
    @Column(length = 10)
    @Builder.Default
    private String currency = "RUB";

    /**
     * Payment URL for redirecting user
     */
    @Column(length = 1024)
    private String paymentUrl;

    /**
     * Order description
     */
    @Column(length = 512)
    private String description;

    /**
     * Payment method used (CARD, SPLIT, etc.)
     */
    @Column(length = 32)
    private String paymentMethod;

    /**
     * Operation ID from Yandex Pay
     */
    @Column(length = 128)
    private String operationId;

    /**
     * Error message if payment failed
     */
    @Column(length = 512)
    private String errorMessage;

    /**
     * Raw webhook payload for debugging
     */
    @Column(columnDefinition = "TEXT")
    private String rawWebhookPayload;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * When the payment expires
     */
    private LocalDateTime expiresAt;

    /**
     * Check if payment is in a final successful state
     */
    public boolean isSuccessful() {
        return STATUS_CAPTURED.equalsIgnoreCase(paymentStatus) ||
               STATUS_CONFIRMED.equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if payment is still pending/processing
     */
    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(paymentStatus) ||
               STATUS_AUTHORIZED.equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if payment has failed or been cancelled
     */
    public boolean isFailed() {
        return STATUS_FAILED.equalsIgnoreCase(paymentStatus) ||
               STATUS_CANCELLED.equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if payment has been refunded
     */
    public boolean isRefunded() {
        return STATUS_REFUNDED.equalsIgnoreCase(paymentStatus);
    }

    /**
     * Mark as successful
     */
    public void markSuccess() {
        this.paymentStatus = STATUS_CONFIRMED;
    }

    /**
     * Mark as failed with error message
     */
    public void markFailed(String error) {
        this.paymentStatus = STATUS_FAILED;
        this.errorMessage = error;
    }

    /**
     * Mark as cancelled
     */
    public void markCancelled() {
        this.paymentStatus = STATUS_CANCELLED;
    }

    /**
     * Mark as refunded
     */
    public void markRefunded() {
        this.paymentStatus = STATUS_REFUNDED;
    }
}
