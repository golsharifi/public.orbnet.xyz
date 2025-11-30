package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Leaderboard view showing top referrers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralLeaderboardView {

    /**
     * Time period for the leaderboard.
     */
    private String period; // "all_time", "monthly", "weekly"

    /**
     * Total participants in the referral program.
     */
    private long totalParticipants;

    /**
     * Current user's rank (if authenticated).
     */
    private Integer currentUserRank;

    /**
     * Current user's stats (if authenticated).
     */
    private LeaderboardEntry currentUserEntry;

    /**
     * Top referrers list.
     */
    private List<LeaderboardEntry> topReferrers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardEntry {
        private int rank;
        private int userId;
        private String username;
        private String displayName; // Masked or public based on user preference
        private BigDecimal totalTokensEarned;
        private long directReferrals;
        private long totalNetworkSize;
        private long totalConversions; // Click to signup conversions
        private BigDecimal conversionRate; // Percentage
    }
}
