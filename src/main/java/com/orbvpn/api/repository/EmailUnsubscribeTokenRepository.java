package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.EmailUnsubscribeToken;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailUnsubscribeTokenRepository extends JpaRepository<EmailUnsubscribeToken, Long> {

    Optional<EmailUnsubscribeToken> findByToken(String token);

    Optional<EmailUnsubscribeToken> findByUserAndCategoryAndUsedFalse(User user, NotificationCategory category);

    List<EmailUnsubscribeToken> findByUser(User user);

    @Query("SELECT t FROM EmailUnsubscribeToken t WHERE t.user = :user AND t.category IS NULL AND t.used = false")
    Optional<EmailUnsubscribeToken> findGlobalTokenByUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM EmailUnsubscribeToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM EmailUnsubscribeToken t WHERE t.user = :user")
    void deleteByUser(@Param("user") User user);

    @Query("SELECT COUNT(t) FROM EmailUnsubscribeToken t WHERE t.used = true AND t.usedAt >= :since")
    long countUnsubscribesSince(@Param("since") LocalDateTime since);

    @Query("SELECT t.category, COUNT(t) FROM EmailUnsubscribeToken t WHERE t.used = true AND t.usedAt >= :since GROUP BY t.category")
    List<Object[]> countUnsubscribesByCategorySince(@Param("since") LocalDateTime since);
}
