package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a NOWPayments cryptocurrency payment.
 * NOWPayments is a cryptocurrency payment gateway supporting 300+ cryptocurrencies.
 */
@Getter
@Setter
@Entity
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "now_payment", indexes = {
        @Index(name = "idx_now_payment_id", columnList = "paymentId"),
        @Index(name = "idx_now_payment_status", columnList = "paymentStatus"),
        @Index(name = "idx_now_payment_order_id", columnList = "orderId")
})
public class NowPayment {

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
     * NOWPayments unique payment ID
     */
    @Column(length = 64)
    private String paymentId;

    /**
     * Internal order ID for reference
     */
    @Column(length = 128)
    private String orderId;

    /**
     * Payment status from NOWPayments:
     * waiting, confirming, confirmed, sending, partially_paid, finished, failed, refunded, expired
     */
    @Column(length = 32)
    private String paymentStatus;

    /**
     * Price amount in fiat currency (e.g., USD)
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal priceAmount;

    /**
     * Fiat currency code (e.g., "usd")
     */
    @Column(length = 10)
    private String priceCurrency;

    /**
     * Amount to pay in cryptocurrency
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal payAmount;

    /**
     * Cryptocurrency code (e.g., "btc", "eth", "ltc")
     */
    @Column(length = 20)
    private String payCurrency;

    /**
     * Cryptocurrency deposit address
     */
    @Column(length = 256)
    private String payAddress;

    /**
     * Amount actually paid (for partial payments)
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal actuallyPaid;

    /**
     * Purchase ID from NOWPayments
     */
    @Column(length = 64)
    private String purchaseId;

    /**
     * Order description
     */
    @Column(length = 512)
    private String orderDescription;

    /**
     * IPN callback URL
     */
    @Column(length = 512)
    private String ipnCallbackUrl;

    /**
     * Outcome amount after conversion (if applicable)
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal outcomeAmount;

    /**
     * Outcome currency
     */
    @Column(length = 20)
    private String outcomeCurrency;

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
        return "finished".equalsIgnoreCase(paymentStatus) ||
               "confirmed".equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if payment is still pending/processing
     */
    public boolean isPending() {
        return "waiting".equalsIgnoreCase(paymentStatus) ||
               "confirming".equalsIgnoreCase(paymentStatus) ||
               "sending".equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if payment has failed or expired
     */
    public boolean isFailed() {
        return "failed".equalsIgnoreCase(paymentStatus) ||
               "expired".equalsIgnoreCase(paymentStatus) ||
               "refunded".equalsIgnoreCase(paymentStatus);
    }
}
