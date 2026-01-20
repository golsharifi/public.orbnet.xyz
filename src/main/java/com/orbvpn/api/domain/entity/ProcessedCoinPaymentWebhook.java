package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking processed CoinPayment IPN (Instant Payment Notification) webhooks.
 * Used for idempotency to prevent duplicate processing of the same webhook event.
 */
@Entity
@Table(name = "processed_coinpayment_webhooks",
    indexes = {
        @Index(name = "idx_coinpayment_webhook_ipn_id", columnList = "ipnId"),
        @Index(name = "idx_coinpayment_webhook_payment_id", columnList = "paymentId"),
        @Index(name = "idx_coinpayment_webhook_processed_at", columnList = "processedAt")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedCoinPaymentWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for the IPN event.
     * Combination of paymentId + txnId + status creates uniqueness.
     */
    @Column(nullable = false, unique = true, length = 255)
    private String ipnId;

    /**
     * The internal payment ID this webhook relates to
     */
    @Column(nullable = false)
    private Long paymentId;

    /**
     * CoinPayments transaction ID (txn_id)
     */
    @Column(length = 100)
    private String txnId;

    /**
     * The IPN status code received
     */
    private Integer status;

    /**
     * The amount received (using BigDecimal for precision)
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal amount;

    /**
     * When this webhook was processed
     */
    @Column(nullable = false)
    private LocalDateTime processedAt;

    /**
     * Processing result - success/failure
     */
    @Column(nullable = false)
    private boolean successful;

    /**
     * Error message if processing failed
     */
    @Column(length = 500)
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = LocalDateTime.now();
        }
    }

    /**
     * Generate a unique IPN ID from payment details
     */
    public static String generateIpnId(Long paymentId, String txnId, int status) {
        return String.format("coinpayment_%d_%s_%d", paymentId, txnId != null ? txnId : "callback", status);
    }
}
