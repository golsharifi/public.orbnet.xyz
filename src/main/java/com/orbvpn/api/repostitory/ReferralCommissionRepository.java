package com.orbvpn.api.repostitory;

import com.orbvpn.api.domain.entity.ReferralCommission;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.ReferralCommissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for referral commission records.
 */
@Repository
public interface ReferralCommissionRepository extends JpaRepository<ReferralCommission, Long> {

    /**
     * Find all commissions earned by a user.
     */
    List<ReferralCommission> findByBeneficiaryOrderByCreatedAtDesc(User beneficiary);

    /**
     * Find commissions by beneficiary with pagination.
     */
    Page<ReferralCommission> findByBeneficiaryOrderByCreatedAtDesc(User beneficiary, Pageable pageable);

    /**
     * Find commissions by status.
     */
    List<ReferralCommission> findByStatus(ReferralCommissionStatus status);

    /**
     * Find pending commissions for a beneficiary.
     */
    List<ReferralCommission> findByBeneficiaryAndStatus(User beneficiary, ReferralCommissionStatus status);

    /**
     * Check if commission already exists for a payment and beneficiary.
     * Prevents duplicate commission records.
     */
    boolean existsByPaymentIdAndBeneficiaryId(Long paymentId, int beneficiaryId);

    /**
     * Get total tokens earned by a user.
     */
    @Query("SELECT COALESCE(SUM(rc.tokenAmount), 0) FROM ReferralCommission rc " +
           "WHERE rc.beneficiary = :user AND rc.status = 'CREDITED'")
    BigDecimal getTotalTokensEarnedByUser(@Param("user") User user);

    /**
     * Get total tokens earned by user at a specific level.
     */
    @Query("SELECT COALESCE(SUM(rc.tokenAmount), 0) FROM ReferralCommission rc " +
           "WHERE rc.beneficiary = :user AND rc.level = :level AND rc.status = 'CREDITED'")
    BigDecimal getTotalTokensEarnedByUserAtLevel(@Param("user") User user, @Param("level") int level);

    /**
     * Count direct referrals (level 1) for a user.
     */
    @Query("SELECT COUNT(DISTINCT rc.sourceUser) FROM ReferralCommission rc " +
           "WHERE rc.beneficiary = :user AND rc.level = 1")
    long countDirectReferrals(@Param("user") User user);

    /**
     * Count total referrals (all levels) for a user.
     */
    @Query("SELECT COUNT(DISTINCT rc.sourceUser) FROM ReferralCommission rc " +
           "WHERE rc.beneficiary = :user")
    long countTotalReferrals(@Param("user") User user);

    /**
     * Get earnings summary by level for a user.
     */
    @Query("SELECT rc.level, COUNT(rc), COALESCE(SUM(rc.tokenAmount), 0) " +
           "FROM ReferralCommission rc " +
           "WHERE rc.beneficiary = :user AND rc.status = 'CREDITED' " +
           "GROUP BY rc.level ORDER BY rc.level")
    List<Object[]> getEarningsSummaryByLevel(@Param("user") User user);

    /**
     * Find commissions in a date range.
     */
    List<ReferralCommission> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Get total pending tokens for a user.
     */
    @Query("SELECT COALESCE(SUM(rc.tokenAmount), 0) FROM ReferralCommission rc " +
           "WHERE rc.beneficiary = :user AND rc.status = 'PENDING'")
    BigDecimal getTotalPendingTokens(@Param("user") User user);

    /**
     * Find all commissions from a specific payment.
     */
    List<ReferralCommission> findByPaymentId(Long paymentId);

    /**
     * Get commission stats for analytics.
     */
    @Query("SELECT rc.status, COUNT(rc), COALESCE(SUM(rc.tokenAmount), 0) " +
           "FROM ReferralCommission rc " +
           "WHERE rc.createdAt BETWEEN :start AND :end " +
           "GROUP BY rc.status")
    List<Object[]> getCommissionStatsByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
