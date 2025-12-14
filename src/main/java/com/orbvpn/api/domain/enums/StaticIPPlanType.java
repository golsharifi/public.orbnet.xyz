package com.orbvpn.api.domain.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Static IP subscription plan types with their pricing and limits.
 */
@Getter
public enum StaticIPPlanType {
    PERSONAL(1, 1, new BigDecimal("6.99"), new BigDecimal("69.99"), "Personal"),
    PRO(1, 2, new BigDecimal("9.99"), new BigDecimal("99.99"), "Pro"),
    MULTI_REGION(3, 1, new BigDecimal("17.99"), new BigDecimal("179.99"), "Multi-Region"),
    BUSINESS(5, 2, new BigDecimal("29.99"), new BigDecimal("299.99"), "Business"),
    ENTERPRISE(10, 3, new BigDecimal("49.99"), new BigDecimal("499.99"), "Enterprise");

    private final int maxRegions;
    private final int portForwardsPerRegion;
    private final BigDecimal priceMonthly;
    private final BigDecimal priceYearly;
    private final String displayName;

    StaticIPPlanType(int maxRegions, int portForwardsPerRegion,
                     BigDecimal priceMonthly, BigDecimal priceYearly, String displayName) {
        this.maxRegions = maxRegions;
        this.portForwardsPerRegion = portForwardsPerRegion;
        this.priceMonthly = priceMonthly;
        this.priceYearly = priceYearly;
        this.displayName = displayName;
    }

    public int getTotalIncludedPortForwards() {
        return maxRegions * portForwardsPerRegion;
    }

    /**
     * Alias for getMaxRegions() for convenience
     */
    public int getRegionsIncluded() {
        return maxRegions;
    }
}
