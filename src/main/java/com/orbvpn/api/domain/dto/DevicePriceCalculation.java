package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for device/multi-login price calculations.
 * Provides detailed breakdown for transparency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePriceCalculation {

    /**
     * Number of devices being purchased/added.
     */
    private int deviceCount;

    /**
     * Remaining days in the subscription (for pro-rata).
     */
    private int remainingDays;

    /**
     * Total duration of the subscription in days.
     */
    private int subscriptionDuration;

    /**
     * Calculated daily rate per device.
     */
    private BigDecimal dailyRatePerDevice;

    /**
     * Price before any discounts applied.
     */
    private BigDecimal priceBeforeDiscount;

    /**
     * Service group discount percentage applied.
     */
    private BigDecimal discountPercent;

    /**
     * Reseller level discount percentage (if applicable).
     */
    private BigDecimal resellerDiscount;

    /**
     * Final price after all discounts.
     */
    private BigDecimal finalPrice;

    /**
     * Currency code (default: USD).
     */
    @Builder.Default
    private String currency = "USD";

    /**
     * When the subscription expires (for pro-rata reference).
     */
    private LocalDateTime subscriptionExpiresAt;

    /**
     * Plan ID if using plan-based pricing.
     */
    private Long planId;

    /**
     * Plan name if using plan-based pricing.
     */
    private String planName;

    /**
     * Duration in days if using plan-based pricing.
     */
    private int durationDays;

    /**
     * Message or additional info.
     */
    private String message;

    /**
     * Calculate savings amount.
     */
    public BigDecimal getSavingsAmount() {
        if (priceBeforeDiscount != null && finalPrice != null) {
            return priceBeforeDiscount.subtract(finalPrice);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Check if this is a pro-rata calculation.
     */
    public boolean isProRata() {
        return remainingDays > 0 && subscriptionDuration > 0;
    }

    /**
     * Check if this is a plan-based calculation.
     */
    public boolean isPlanBased() {
        return planId != null;
    }
}
