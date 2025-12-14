package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Paginated view of a user's referral network.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralNetworkPageView {

    /**
     * Total count of users across all levels.
     */
    private long totalNetworkSize;

    /**
     * Total earnings from all referrals.
     */
    private BigDecimal totalEarnings;

    /**
     * Paginated users at the requested level.
     */
    private ReferralLevelPage levelPage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferralLevelPage {
        private int level;
        private String levelName;
        private BigDecimal commissionPercent;
        private long totalUsersAtLevel;
        private BigDecimal totalEarningsFromLevel;
        private int page;
        private int pageSize;
        private int totalPages;
        private List<ReferredUserView> users;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferredUserView {
        private int userId;
        private String email;
        private String username;
        private LocalDateTime joinedAt;
        private BigDecimal totalCommissionFromUser;
        private boolean hasActiveSubscription;
        private int directReferralsCount; // How many people this user referred
    }
}
