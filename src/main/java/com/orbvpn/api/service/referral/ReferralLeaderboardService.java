package com.orbvpn.api.service.referral;

import com.orbvpn.api.domain.dto.ReferralLeaderboardView;
import com.orbvpn.api.domain.dto.ReferralLeaderboardView.LeaderboardEntry;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repostitory.ReferralCommissionRepository;
import com.orbvpn.api.repostitory.ReferralLinkClickRepository;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for referral leaderboard functionality.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralLeaderboardService {

    private final ReferralCommissionRepository commissionRepository;
    private final ReferralLinkClickRepository clickRepository;
    private final UserRepository userRepository;

    /**
     * Get the referral leaderboard.
     *
     * @param period "all_time", "monthly", or "weekly"
     * @param limit Maximum entries to return
     * @param currentUser Current authenticated user (for rank lookup)
     * @return Leaderboard view
     */
    public ReferralLeaderboardView getLeaderboard(String period, int limit, User currentUser) {
        log.info("Getting {} referral leaderboard (limit: {})", period, limit);

        LocalDateTime startDate = getStartDateForPeriod(period);

        // Get all users who have earned commissions
        List<User> usersWithCommissions = getUsersWithCommissions(startDate);
        long totalParticipants = usersWithCommissions.size();

        // Build entries and sort by earnings
        List<LeaderboardEntry> entries = usersWithCommissions.stream()
                .map(user -> buildLeaderboardEntry(user, startDate))
                .sorted(Comparator.comparing(LeaderboardEntry::getTotalTokensEarned).reversed())
                .collect(Collectors.toList());

        // Assign ranks
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        // Find current user's rank and entry
        Integer currentUserRank = null;
        LeaderboardEntry currentUserEntry = null;
        if (currentUser != null) {
            for (LeaderboardEntry entry : entries) {
                if (entry.getUserId() == currentUser.getId()) {
                    currentUserRank = entry.getRank();
                    currentUserEntry = entry;
                    break;
                }
            }
        }

        // Limit results
        List<LeaderboardEntry> topReferrers = entries.stream()
                .limit(limit)
                .collect(Collectors.toList());

        return ReferralLeaderboardView.builder()
                .period(period)
                .totalParticipants(totalParticipants)
                .currentUserRank(currentUserRank)
                .currentUserEntry(currentUserEntry)
                .topReferrers(topReferrers)
                .build();
    }

    private LocalDateTime getStartDateForPeriod(String period) {
        return switch (period.toLowerCase()) {
            case "weekly" -> LocalDateTime.now().minusWeeks(1);
            case "monthly" -> LocalDateTime.now().minusMonths(1);
            default -> LocalDateTime.of(2000, 1, 1, 0, 0); // all_time
        };
    }

    private List<User> getUsersWithCommissions(LocalDateTime startDate) {
        // This is a simplified implementation
        // In production, use a custom query for better performance
        return userRepository.findAll().stream()
                .filter(u -> commissionRepository.getTotalTokensEarnedByUser(u)
                        .compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }

    private LeaderboardEntry buildLeaderboardEntry(User user, LocalDateTime startDate) {
        BigDecimal totalEarned = commissionRepository.getTotalTokensEarnedByUser(user);
        long directReferrals = commissionRepository.countDirectReferrals(user);
        long totalNetworkSize = commissionRepository.countTotalReferrals(user);

        // Click stats
        long totalClicks = clickRepository.countByReferrerId(user.getId());
        long conversions = clickRepository.countByReferrerIdAndConvertedTrue(user.getId());
        BigDecimal conversionRate = totalClicks > 0
                ? BigDecimal.valueOf(conversions * 100.0 / totalClicks).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Mask username for privacy
        String displayName = maskUsername(user.getUsername());

        return LeaderboardEntry.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(displayName)
                .totalTokensEarned(totalEarned)
                .directReferrals(directReferrals)
                .totalNetworkSize(totalNetworkSize)
                .totalConversions(conversions)
                .conversionRate(conversionRate)
                .build();
    }

    private String maskUsername(String username) {
        if (username == null || username.length() < 3) {
            return "***";
        }
        return username.substring(0, 2) + "***" + username.substring(username.length() - 1);
    }
}
