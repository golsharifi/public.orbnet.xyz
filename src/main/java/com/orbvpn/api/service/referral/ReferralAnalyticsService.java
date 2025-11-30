package com.orbvpn.api.service.referral;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repostitory.ReferralCommissionRepository;
import com.orbvpn.api.repostitory.ReferralLevelRepository;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for referral analytics and reporting.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralAnalyticsService {

    private final ReferralCommissionRepository commissionRepository;
    private final ReferralLevelRepository levelRepository;
    private final UserRepository userRepository;

    /**
     * Get global referral statistics.
     */
    public ReferralStats getGlobalStats(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        List<Object[]> stats = commissionRepository.getCommissionStatsByDateRange(startDate, endDate);

        BigDecimal totalCredited = BigDecimal.ZERO;
        BigDecimal totalPending = BigDecimal.ZERO;
        long creditedCount = 0;
        long pendingCount = 0;

        for (Object[] stat : stats) {
            String status = (String) stat[0];
            long count = (Long) stat[1];
            BigDecimal tokens = (BigDecimal) stat[2];

            switch (status) {
                case "CREDITED":
                    totalCredited = tokens;
                    creditedCount = count;
                    break;
                case "PENDING":
                    totalPending = tokens;
                    pendingCount = count;
                    break;
            }
        }

        // Count users with referrals
        long usersWithReferrers = userRepository.countByReferredByIsNotNull();

        return ReferralStats.builder()
                .totalTokensCredited(totalCredited)
                .totalTokensPending(totalPending)
                .creditedCommissions(creditedCount)
                .pendingCommissions(pendingCount)
                .totalUsersReferred(usersWithReferrers)
                .activeLevels(levelRepository.findByActiveTrueOrderByLevelAsc().size())
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    /**
     * Get top referrers by total earnings.
     */
    public List<TopReferrerView> getTopReferrers(int limit) {
        // This would require a custom query to aggregate by beneficiary
        // For now, returning empty list as placeholder
        return List.of();
    }

    /**
     * Get referral network depth for a user.
     */
    public int getReferralNetworkDepth(User user) {
        int depth = 0;
        User current = user;

        while (current.getReferredBy() != null && depth < 10) {
            current = current.getReferredBy();
            depth++;
        }

        return depth;
    }

    /**
     * Get the number of users in each level of a user's downline.
     */
    public Map<Integer, Long> getDownlineByLevel(User user, int maxLevels) {
        Map<Integer, Long> downlineByLevel = new HashMap<>();

        // This would require a recursive CTE query for efficiency
        // For now, just count direct referrals
        long directReferrals = userRepository.countByReferredById(user.getId());
        if (directReferrals > 0) {
            downlineByLevel.put(1, directReferrals);
        }

        return downlineByLevel;
    }

    @lombok.Data
    @lombok.Builder
    public static class ReferralStats {
        private BigDecimal totalTokensCredited;
        private BigDecimal totalTokensPending;
        private long creditedCommissions;
        private long pendingCommissions;
        private long totalUsersReferred;
        private int activeLevels;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class TopReferrerView {
        private int userId;
        private String username;
        private String email;
        private BigDecimal totalTokensEarned;
        private long directReferrals;
        private long totalCommissions;
    }
}
