package com.orbvpn.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * View of a user's referral network showing people they've referred.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralNetworkView {

    /**
     * Total count of users in the network across all levels.
     */
    private long totalNetworkSize;

    /**
     * Users at each level.
     */
    private List<ReferralLevelNetwork> levels;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferralLevelNetwork {
        private int level;
        private String levelName;
        private long userCount;
        private BigDecimal totalEarningsFromLevel;
        private List<ReferredUserView> users;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferredUserView {
        private int userId;
        private String email; // Masked for privacy
        private String username;
        private LocalDateTime joinedAt;
        private BigDecimal totalCommissionFromUser;
        private boolean hasActiveSubscription;
    }
}
