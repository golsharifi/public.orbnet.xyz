package com.orbvpn.api.repository;

import com.orbvpn.api.domain.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.GatewayName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Integer> {
        // Basic queries
        UserSubscription findFirstByUserOrderByCreatedAtDesc(User user);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        Optional<UserSubscription> findByUserId(long userId);

        @Query("SELECT COUNT(s) > 0 FROM UserSubscription s WHERE s.purchaseToken = :purchaseToken AND s.status = :status")
        boolean existsByPurchaseTokenAndStatus(@Param("purchaseToken") String purchaseToken,
                        @Param("status") SubscriptionStatus status);

        // Payment gateway specific queries
        UserSubscription findByPurchaseToken(String purchaseToken);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT us FROM UserSubscription us WHERE us.purchaseToken = :purchaseToken")
        Optional<UserSubscription> findByPurchaseTokenWithLock(@Param("purchaseToken") String purchaseToken);

        UserSubscription findByOriginalTransactionId(String originalTransactionId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT us FROM UserSubscription us WHERE us.originalTransactionId = :originalTransactionId")
        Optional<UserSubscription> findByOriginalTransactionIdWithLock(
                        @Param("originalTransactionId") String originalTransactionId);

        Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT us FROM UserSubscription us WHERE us.stripeSubscriptionId = :stripeSubscriptionId")
        Optional<UserSubscription> findByStripeSubscriptionIdWithLock(
                        @Param("stripeSubscriptionId") String stripeSubscriptionId);

        Optional<UserSubscription> findByStripeCustomerId(String stripeCustomerId);

        Optional<UserSubscription> findByPayment(Payment payment);

        // Deletion operations
        @Transactional
        @Modifying
        @Query(value = """
                        DELETE FROM user_subscription
                        WHERE user_id = :userId AND (version = :version OR version IS NULL)
                        """, nativeQuery = true)
        int deleteByUserIdAndVersion(@Param("userId") long userId, @Param("version") Long version);

        @Transactional
        @Modifying
        @Query(value = "DELETE FROM user_subscription WHERE user_id = :userId", nativeQuery = true)
        int deleteByUserId(@Param("userId") long userId);

        @Query("""
                        SELECT us
                        FROM UserSubscription us
                        WHERE us.user.id = :userId
                        """)
        Optional<UserSubscription> findByUserIdWithVersion(@Param("userId") long userId);

        // Subscription status queries
        List<UserSubscription> findByIsTokenBasedTrue();

        List<UserSubscription> findByGatewayAndStatus(GatewayName gateway, SubscriptionStatus status);

        @Query("SELECT u FROM UserSubscription u WHERE u.gateway = :gateway AND u.status = :status AND u.expiresAt < :date")
        List<UserSubscription> findExpiredSubscriptions(
                        @Param("gateway") GatewayName gateway,
                        @Param("status") SubscriptionStatus status,
                        @Param("date") LocalDateTime date);

        // Statistics queries
        @Query("select count(sub.id) from UserSubscription sub where sub.createdAt > :createdAt")
        int countTotalSubscriptionCount(@Param("createdAt") LocalDateTime createdAt);

        @Query("select sum(sub.price) from UserSubscription sub where sub.createdAt > :createdAt")
        BigDecimal getTotalSubscriptionPrice(@Param("createdAt") LocalDateTime createdAt);

        // Reseller related queries
        @Query("select count(sub.id) from UserSubscription sub " +
                        "where sub.expiresAt > current_date and sub.user.reseller = :reseller")
        int countResellerActiveSubscriptions(Reseller reseller);

        @Query("SELECT COUNT(sub.id) FROM UserSubscription sub " +
                        "WHERE sub.expiresAt > CURRENT_DATE " +
                        "AND sub.user.reseller.level.name <> com.orbvpn.api.domain.enums.ResellerLevelName.OWNER")
        int countAllResellersActiveSubscriptions();

        @Query("select sum(sub.price) from UserSubscription sub " +
                        "where sub.createdAt > :createdAt and sub.user.reseller = :reseller")
        BigDecimal getResellerTotalSale(
                        @Param("reseller") Reseller reseller,
                        @Param("createdAt") LocalDateTime createdAt);

        @Query("select sum(sub.price) from UserSubscription sub " +
                        "where sub.createdAt > :createdAt " +
                        "and sub.user.reseller.level.name <> com.orbvpn.api.domain.enums.ResellerLevelName.OWNER")
        BigDecimal getAllResellerTotalSale(@Param("createdAt") LocalDateTime createdAt);

        // Expiration queries
        @Query("select sub.user.profile from UserSubscription sub " +
                        "where sub.expiresAt >= :startTime and sub.expiresAt <= :endTime")
        List<UserProfile> getUsersExpireBetween(
                        LocalDateTime startTime,
                        LocalDateTime endTime);

        @Query("SELECT us FROM UserSubscription us " +
                        "WHERE us.user.id = :userId " +
                        "AND (us.expiresAt IS NULL OR us.expiresAt > CURRENT_TIMESTAMP) " +
                        "ORDER BY us.createdAt DESC")
        Optional<UserSubscription> findCurrentSubscription(@Param("userId") int userId);

        @Modifying
        @Query(value = """
                        DELETE FROM user_subscription
                        WHERE user_id = :userId
                        """, nativeQuery = true)
        int deleteByUserIdForced(@Param("userId") long userId);

        // Add this method to get user ID as long
        @Query("SELECT u.id FROM User u WHERE u = :user")
        long getUserId(@Param("user") User user);

        @Query("SELECT us FROM UserSubscription us WHERE us.user = :user")
        List<UserSubscription> findAllByUser(@Param("user") User user);

        @Modifying
        @Query(value = """
                        UPDATE user_subscription
                        SET payment_id = NULL
                        WHERE user_id = :userId
                        """, nativeQuery = true)
        int detachPayments(@Param("userId") long userId);

        @Modifying
        @Query(value = """
                        DELETE FROM user_subscription
                        WHERE user_id = :userId
                        """, nativeQuery = true)
        int deleteSubscriptions(@Param("userId") long userId);

        @Modifying
        @Query(value = "UPDATE user_subscription SET version = 0 WHERE version IS NULL", nativeQuery = true)
        void initializeNullVersions();

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT us FROM UserSubscription us WHERE us.user.id = :userId")
        Optional<UserSubscription> findByUserIdWithLock(@Param("userId") long userId);

        @Query("SELECT us FROM UserSubscription us WHERE us.user = :user")
        List<UserSubscription> findByUser(@Param("user") User user);

        List<UserSubscription> findByGatewayAndAcknowledgedFalse(GatewayName gateway);

        Optional<UserSubscription> findByUserAndStatus(User user, SubscriptionStatus status);

        @Query("SELECT sh FROM SubscriptionHistory sh WHERE sh.subscription.id = :subscriptionId")
        List<SubscriptionHistory> findSubscriptionHistoriesBySubscriptionId(
                        @Param("subscriptionId") Integer subscriptionId);

        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                        "AND s.expiresAt > CURRENT_TIMESTAMP")
        long countActiveSubscriptions();

        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'EXPIRED' " +
                        "OR (s.status = 'ACTIVE' AND s.expiresAt <= CURRENT_TIMESTAMP)")
        long countExpiredSubscriptions();

        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.isTrialPeriod = true " +
                        "AND s.status = 'ACTIVE' AND s.expiresAt > CURRENT_TIMESTAMP")
        long countTrialSubscriptions();

        // Additional helper queries for subscription management
        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                        "AND s.expiresAt BETWEEN :start AND :end")
        long countSubscriptionsExpiringBetween(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                        "AND s.gateway = :gateway")
        long countActiveSubscriptionsByGateway(@Param("gateway") GatewayName gateway);

        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                        "AND s.autoRenew = true")
        long countAutoRenewSubscriptions();

        // Additional utility method for monitoring
        @Query(value = """
                        SELECT DATE(s.created_at) as date, COUNT(*) as count
                        FROM user_subscription s
                        WHERE s.created_at >= :startDate
                        GROUP BY DATE(s.created_at)
                        ORDER BY date DESC
                        """, nativeQuery = true)
        List<Object[]> getSubscriptionTrendsByDate(@Param("startDate") LocalDateTime startDate);

        List<UserSubscription> findByStatusAndGateway(SubscriptionStatus status, GatewayName gateway);

        // In UserSubscriptionRepository.java
        List<UserSubscription> findByStatusAndExpiresAtBefore(
                        SubscriptionStatus status,
                        LocalDateTime dateTime);

        @Modifying
        @Query("UPDATE UserSubscription u SET u.price = :price, u.currency = :currency WHERE u.id = :id")
        int updatePriceAndCurrency(@Param("id") int id, @Param("price") BigDecimal price,
                        @Param("currency") String currency);

        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                        "AND s.gateway = :gateway AND s.price IS NOT NULL")
        long countActiveSubscriptionsWithPrice(@Param("gateway") GatewayName gateway);

        @Query("SELECT AVG(s.price) FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                        "AND s.gateway = :gateway AND s.price IS NOT NULL")
        BigDecimal getAveragePriceByGateway(@Param("gateway") GatewayName gateway);

        // Bandwidth tracking queries

        /**
         * Count subscriptions with bandwidth quota set
         */
        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.bandwidthQuotaBytes IS NOT NULL AND s.status = 'ACTIVE'")
        long countByBandwidthQuotaBytesNotNull();

        /**
         * Count subscriptions with unlimited bandwidth (null quota)
         */
        @Query("SELECT COUNT(s) FROM UserSubscription s WHERE s.bandwidthQuotaBytes IS NULL AND s.status = 'ACTIVE'")
        long countByBandwidthQuotaBytesNull();

        /**
         * Find users who have exceeded their bandwidth quota
         */
        @Query("SELECT s FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                "AND s.bandwidthQuotaBytes IS NOT NULL " +
                "AND s.bandwidthUsedBytes >= (s.bandwidthQuotaBytes + COALESCE(s.bandwidthAddonBytes, 0)) " +
                "ORDER BY s.bandwidthUsedBytes DESC")
        List<UserSubscription> findUsersExceedingBandwidth();

        /**
         * Find users near bandwidth limit (usage percent >= threshold)
         */
        @Query("SELECT s FROM UserSubscription s WHERE s.status = 'ACTIVE' " +
                "AND s.bandwidthQuotaBytes IS NOT NULL " +
                "AND s.bandwidthUsedBytes > 0 " +
                "AND (CAST(s.bandwidthUsedBytes AS double) / CAST((s.bandwidthQuotaBytes + COALESCE(s.bandwidthAddonBytes, 0)) AS double) * 100.0) >= :thresholdPercent " +
                "AND s.bandwidthUsedBytes < (s.bandwidthQuotaBytes + COALESCE(s.bandwidthAddonBytes, 0)) " +
                "ORDER BY (CAST(s.bandwidthUsedBytes AS double) / CAST((s.bandwidthQuotaBytes + COALESCE(s.bandwidthAddonBytes, 0)) AS double)) DESC")
        List<UserSubscription> findUsersNearBandwidthLimit(@Param("thresholdPercent") double thresholdPercent);

        /**
         * Sum total bandwidth quota for all active subscriptions
         */
        @Query("SELECT COALESCE(SUM(s.bandwidthQuotaBytes + COALESCE(s.bandwidthAddonBytes, 0)), 0) FROM UserSubscription s WHERE s.status = 'ACTIVE' AND s.bandwidthQuotaBytes IS NOT NULL")
        Long sumTotalBandwidthQuota();

}
