package com.orbvpn.api.domain.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Partner datacenter tiers with their benefits.
 */
@Getter
public enum PartnerTier {
    BRONZE(1, 5, 100, new BigDecimal("10.00"), new BigDecimal("1.00"), "Bronze"),
    SILVER(5, 20, 500, new BigDecimal("15.00"), new BigDecimal("1.50"), "Silver"),
    GOLD(20, 100, 1000, new BigDecimal("20.00"), new BigDecimal("2.00"), "Gold"),
    PLATINUM(100, Integer.MAX_VALUE, 10000, new BigDecimal("25.00"), new BigDecimal("3.00"), "Platinum");

    private final int minIps;
    private final int maxIps;
    private final int minBandwidthMbps;
    private final BigDecimal revenueSharePercent;
    private final BigDecimal tokenBonusMultiplier;
    private final String displayName;

    PartnerTier(int minIps, int maxIps, int minBandwidthMbps,
                BigDecimal revenueSharePercent, BigDecimal tokenBonusMultiplier,
                String displayName) {
        this.minIps = minIps;
        this.maxIps = maxIps;
        this.minBandwidthMbps = minBandwidthMbps;
        this.revenueSharePercent = revenueSharePercent;
        this.tokenBonusMultiplier = tokenBonusMultiplier;
        this.displayName = displayName;
    }

    public static PartnerTier fromIpCount(int ipCount) {
        for (PartnerTier tier : values()) {
            if (ipCount >= tier.minIps && ipCount <= tier.maxIps) {
                return tier;
            }
        }
        return BRONZE;
    }
}
