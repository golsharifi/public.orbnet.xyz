package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.ReferralCommissionStatus;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records each referral commission earned.
 * Tracks who earned it, from which user's purchase, at what level,
 * and the token amount awarded.
 */
@Entity
@Table(name = "referral_commission", indexes = {
    @Index(name = "idx_referral_commission_beneficiary", columnList = "beneficiary_id"),
    @Index(name = "idx_referral_commission_source", columnList = "source_user_id"),
    @Index(name = "idx_referral_commission_payment", columnList = "payment_id"),
    @Index(name = "idx_referral_commission_status", columnList = "status"),
    @Index(name = "idx_referral_commission_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ReferralCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who earns this commission (the referrer up the chain)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private User beneficiary;

    /**
     * The user whose payment triggered this commission
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_user_id", nullable = false)
    private User sourceUser;

    /**
     * The payment that triggered this commission
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /**
     * The referral level (1 = direct, 2 = indirect, etc.)
     */
    @Column(nullable = false)
    private int level;

    /**
     * The original payment amount
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount;

    /**
     * The commission percentage applied
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /**
     * Token amount earned
     */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal tokenAmount;

    /**
     * Token rate used for conversion (tokens per currency unit)
     */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal tokenRate;

    /**
     * Status of this commission
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReferralCommissionStatus status = ReferralCommissionStatus.PENDING;

    /**
     * When the tokens were credited to the beneficiary's balance
     */
    private LocalDateTime creditedAt;

    /**
     * Reference to the token transaction created (if credited)
     */
    @Column(name = "token_transaction_id")
    private Long tokenTransactionId;

    /**
     * Any notes or additional info
     */
    @Column(length = 500)
    private String notes;

    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Mark this commission as credited.
     */
    public void markCredited(Long transactionId) {
        this.status = ReferralCommissionStatus.CREDITED;
        this.creditedAt = LocalDateTime.now();
        this.tokenTransactionId = transactionId;
    }

    /**
     * Mark this commission as failed.
     */
    public void markFailed(String reason) {
        this.status = ReferralCommissionStatus.FAILED;
        this.notes = reason;
    }
}
