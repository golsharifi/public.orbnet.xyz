package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Click tracking statistics for referral links.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralClickStatsView {

    /**
     * Total clicks on user's referral link.
     */
    private long totalClicks;

    /**
     * Total registrations from clicks.
     */
    private long totalConversions;

    /**
     * Conversion rate (conversions / clicks * 100).
     */
    private BigDecimal conversionRate;

    /**
     * Clicks by country.
     */
    private List<CountryStats> clicksByCountry;

    /**
     * Daily click/conversion trends.
     */
    private List<DailyStats> dailyStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryStats {
        private String countryCode;
        private String countryName;
        private long clicks;
        private long conversions;
        private BigDecimal conversionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStats {
        private LocalDate date;
        private long clicks;
        private long conversions;
    }
}
