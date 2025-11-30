package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * View of a user's referral earnings for GraphQL responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralEarningsView {

    /**
     * Total tokens earned from referrals (all levels combined).
     */
    private BigDecimal totalTokensEarned;

    /**
     * Tokens pending credit.
     */
    private BigDecimal pendingTokens;

    /**
     * Number of users directly referred (level 1).
     */
    private long directReferrals;

    /**
     * Total users in the referral network (all levels).
     */
    private long totalReferrals;

    /**
     * Earnings breakdown by level.
     */
    private List<LevelEarningsView> levelEarnings;

    /**
     * The user's referral code for sharing.
     */
    private String referralCode;

    /**
     * Shareable referral link.
     */
    private String referralLink;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelEarningsView {
        private int level;
        private String levelName;
        private long commissionCount;
        private BigDecimal tokensEarned;
        private BigDecimal commissionPercent;
    }
}
