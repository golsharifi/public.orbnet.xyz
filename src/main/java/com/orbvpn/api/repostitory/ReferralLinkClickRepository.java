package com.orbvpn.api.repostitory;

import com.orbvpn.api.domain.entity.ReferralLinkClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for referral link click tracking.
 */
@Repository
public interface ReferralLinkClickRepository extends JpaRepository<ReferralLinkClick, Long> {

    /**
     * Count total clicks for a referral code.
     */
    long countByReferralCode(String referralCode);

    /**
     * Count clicks for a user's referral code.
     */
    long countByReferrerId(int referrerId);

    /**
     * Count converted clicks (registrations) for a referral code.
     */
    long countByReferralCodeAndConvertedTrue(String referralCode);

    /**
     * Count conversions for a user.
     */
    long countByReferrerIdAndConvertedTrue(int referrerId);

    /**
     * Get clicks in a date range.
     */
    List<ReferralLinkClick> findByReferrerIdAndCreatedAtBetween(
            int referrerId, LocalDateTime start, LocalDateTime end);

    /**
     * Check if an IP has clicked recently (for rate limiting / fraud prevention).
     */
    @Query("SELECT COUNT(c) FROM ReferralLinkClick c " +
           "WHERE c.ipHash = :ipHash AND c.createdAt > :since")
    long countRecentClicksByIp(@Param("ipHash") String ipHash, @Param("since") LocalDateTime since);

    /**
     * Get click stats by country for a user.
     */
    @Query("SELECT c.country, COUNT(c), SUM(CASE WHEN c.converted THEN 1 ELSE 0 END) " +
           "FROM ReferralLinkClick c " +
           "WHERE c.referrer.id = :userId " +
           "GROUP BY c.country")
    List<Object[]> getClickStatsByCountry(@Param("userId") int userId);

    /**
     * Get daily click stats for a user.
     */
    @Query("SELECT CAST(c.createdAt AS date), COUNT(c), SUM(CASE WHEN c.converted THEN 1 ELSE 0 END) " +
           "FROM ReferralLinkClick c " +
           "WHERE c.referrer.id = :userId AND c.createdAt BETWEEN :start AND :end " +
           "GROUP BY CAST(c.createdAt AS date) " +
           "ORDER BY CAST(c.createdAt AS date)")
    List<Object[]> getDailyClickStats(
            @Param("userId") int userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
