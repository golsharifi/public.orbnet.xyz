package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.StaticIPSubscription;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.StaticIPPlanType;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StaticIPSubscriptionRepository extends JpaRepository<StaticIPSubscription, Long> {

    // Find active subscription for user
    @Query("SELECT s FROM StaticIPSubscription s WHERE s.user = :user AND s.status = 'ACTIVE' " +
           "AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)")
    Optional<StaticIPSubscription> findActiveByUser(@Param("user") User user);

    // Find by user ID
    List<StaticIPSubscription> findByUserId(int userId);

    // Find by user with lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StaticIPSubscription s WHERE s.user.id = :userId")
    List<StaticIPSubscription> findByUserIdWithLock(@Param("userId") int userId);

    // Find by status
    List<StaticIPSubscription> findByStatus(SubscriptionStatus status);

    // Find expiring subscriptions
    @Query("SELECT s FROM StaticIPSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.expiresAt BETWEEN :start AND :end")
    List<StaticIPSubscription> findExpiringBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Find by external subscription ID
    Optional<StaticIPSubscription> findByExternalSubscriptionId(String externalSubscriptionId);

    // Count by plan type
    @Query("SELECT s.planType, COUNT(s) FROM StaticIPSubscription s WHERE s.status = 'ACTIVE' GROUP BY s.planType")
    List<Object[]> countByPlanType();

    // Get total revenue
    @Query("SELECT SUM(s.priceMonthly) FROM StaticIPSubscription s WHERE s.status = 'ACTIVE'")
    java.math.BigDecimal getTotalMonthlyRevenue();

    // Count active subscriptions
    @Query("SELECT COUNT(s) FROM StaticIPSubscription s WHERE s.status = 'ACTIVE' " +
           "AND (s.expiresAt IS NULL OR s.expiresAt > CURRENT_TIMESTAMP)")
    long countActive();

    // Find by user and status
    Optional<StaticIPSubscription> findByUserAndStatus(User user, SubscriptionStatus status);

    // Find subscriptions needing renewal reminder
    @Query("SELECT s FROM StaticIPSubscription s WHERE s.status = 'ACTIVE' " +
           "AND s.autoRenew = false " +
           "AND s.expiresAt BETWEEN CURRENT_TIMESTAMP AND :reminderDate")
    List<StaticIPSubscription> findNeedingRenewalReminder(@Param("reminderDate") LocalDateTime reminderDate);
}
