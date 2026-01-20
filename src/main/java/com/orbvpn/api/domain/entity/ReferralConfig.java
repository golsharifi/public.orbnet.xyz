package com.orbvpn.api.domain.entity;

import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Global configuration for the referral/MLM system.
 * Single row table for system-wide settings.
 */
@Entity
@Table(name = "referral_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ReferralConfig {

    @Id
    @Builder.Default
    private Long id = 1L; // Single row

    /**
     * Whether the referral system is enabled globally.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Whether users need an active subscription to earn commissions.
     * If true, inactive users won't receive commission credits.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean requireActiveSubscription = true;

    /**
     * Cooling period in days for new accounts before they can earn commissions.
     * Prevents fraud from quickly created accounts.
     */
    @Column(nullable = false)
    @Builder.Default
    private int coolingPeriodDays = 0;

    /**
     * Minimum payment amount to trigger commission calculation.
     * Small payments below this won't generate commissions.
     */
    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal minimumPaymentAmount = BigDecimal.ZERO;

    /**
     * Minimum token balance required before user can withdraw/redeem.
     */
    @Column(precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal minimumWithdrawalTokens = new BigDecimal("10.00");

    /**
     * Maximum commission tokens a single user can earn per day.
     * Zero means no limit.
     */
    @Column(precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal dailyEarningCap = BigDecimal.ZERO;

    /**
     * Maximum commission tokens a single user can earn per month.
     * Zero means no limit.
     */
    @Column(precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal monthlyEarningCap = BigDecimal.ZERO;

    /**
     * Token rate: how many tokens per $1 USD.
     * Default is 1:1.
     */
    @Column(precision = 19, scale = 8, nullable = false)
    @Builder.Default
    private BigDecimal tokenRate = BigDecimal.ONE;

    /**
     * Whether to send notifications when users earn commissions.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean notificationsEnabled = true;

    /**
     * Base URL for referral links.
     */
    @Column(length = 500)
    @Builder.Default
    private String referralBaseUrl = "https://orbvpn.com/invite/";

    /**
     * Whether to track referral link clicks.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean clickTrackingEnabled = true;

    /**
     * Maximum levels to process (0 = use all active levels).
     */
    @Column(nullable = false)
    @Builder.Default
    private int maxLevels = 0;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
