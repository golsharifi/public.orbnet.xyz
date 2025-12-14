package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuration for multi-level marketing referral commission rates.
 * Each level defines what percentage of a subscription payment is awarded
 * as tokens to the referrer at that level.
 *
 * Example:
 * - Level 1 (direct referral): 10% - User A refers User B, A gets 10%
 * - Level 2: 5% - User A refers User B, B refers User C, A gets 5% from C
 * - Level 3: 2% - A -> B -> C -> D, A gets 2% from D's payment
 */
@Entity
@Table(name = "referral_level", indexes = {
    @Index(name = "idx_referral_level_level", columnList = "level"),
    @Index(name = "idx_referral_level_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ReferralLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The level number (1 = direct referral, 2 = referral's referral, etc.)
     */
    @Column(nullable = false, unique = true)
    private int level;

    /**
     * Commission percentage for this level (e.g., 10.00 for 10%)
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /**
     * Human-readable name for this level
     */
    @Column(length = 100)
    private String name;

    /**
     * Description of this level
     */
    @Column(length = 500)
    private String description;

    /**
     * Minimum amount of tokens that can be earned at this level
     * (to prevent micro-transactions)
     */
    @Column(precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal minimumTokens = BigDecimal.ZERO;

    /**
     * Maximum amount of tokens that can be earned per transaction at this level
     * (to cap large payouts)
     */
    @Column(precision = 19, scale = 8)
    private BigDecimal maximumTokens;

    /**
     * Whether this level is currently active
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Calculate token reward for a given payment amount.
     *
     * @param paymentAmount The subscription payment amount
     * @param tokenRate How many tokens per dollar/currency unit
     * @return The token reward amount
     */
    public BigDecimal calculateTokenReward(BigDecimal paymentAmount, BigDecimal tokenRate) {
        if (!active || paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Calculate: paymentAmount * (commissionPercent / 100) * tokenRate
        BigDecimal reward = paymentAmount
            .multiply(commissionPercent)
            .divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP)
            .multiply(tokenRate);

        // Apply minimum
        if (minimumTokens != null && reward.compareTo(minimumTokens) < 0) {
            return BigDecimal.ZERO; // Below minimum, don't award
        }

        // Apply maximum cap
        if (maximumTokens != null && reward.compareTo(maximumTokens) > 0) {
            return maximumTokens;
        }

        return reward;
    }
}
