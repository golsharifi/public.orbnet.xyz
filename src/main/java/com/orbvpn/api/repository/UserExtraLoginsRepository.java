package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.UserExtraLogins;
import com.orbvpn.api.domain.entity.ExtraLoginsPlan;
import com.orbvpn.api.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserExtraLoginsRepository extends JpaRepository<UserExtraLogins, Long> {
    List<UserExtraLogins> findByUserAndActiveTrue(User user);

    List<UserExtraLogins> findByExpiryDateBeforeAndActive(LocalDateTime dateTime, boolean active);

    Optional<UserExtraLogins> findBySubscriptionId(String subscriptionId);

    boolean existsByPlanAndActiveTrue(ExtraLoginsPlan plan);

    @Query("SELECT uel FROM UserExtraLogins uel WHERE uel.user = :user AND uel.active = true AND " +
            "(uel.expiryDate IS NULL OR uel.expiryDate > CURRENT_TIMESTAMP)")
    List<UserExtraLogins> findActiveAndValidByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(uel.loginCount), 0) FROM UserExtraLogins uel " +
            "WHERE uel.user = :user AND uel.active = true AND " +
            "(uel.expiryDate IS NULL OR uel.expiryDate > CURRENT_TIMESTAMP)")
    int getTotalActiveLoginCount(@Param("user") User user);

    List<UserExtraLogins> findByUser(User user);

    @Query("SELECT uel FROM UserExtraLogins uel WHERE uel.expiryDate <= :now AND uel.active = true")
    List<UserExtraLogins> findExpiredLogins(@Param("now") LocalDateTime now);

    @Query("SELECT uel FROM UserExtraLogins uel WHERE " +
            "uel.expiryDate BETWEEN :startDate AND :endDate AND uel.active = true")
    List<UserExtraLogins> findExpiringBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    void deleteByUser(User user);
}