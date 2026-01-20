package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MagicLinkToken;
import com.orbvpn.api.domain.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {

    /**
     * Find a valid magic link token by token string
     */
    @Query("SELECT m FROM MagicLinkToken m WHERE m.token = :token AND m.used = false AND m.expiresAt > :now")
    Optional<MagicLinkToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);

    /**
     * Find the latest valid (not used, not expired) magic link token for a user
     */
    @Query("SELECT m FROM MagicLinkToken m WHERE m.user = :user AND m.used = false AND m.expiresAt > :now ORDER BY m.createdAt DESC LIMIT 1")
    Optional<MagicLinkToken> findLatestValidTokenByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Mark all unused tokens for a user as used (to invalidate old tokens when a new one is generated)
     */
    @Modifying
    @Query("UPDATE MagicLinkToken m SET m.used = true WHERE m.user = :user AND m.used = false")
    int invalidateAllTokensForUser(@Param("user") User user);

    /**
     * Delete expired tokens (for cleanup purposes)
     */
    @Modifying
    @Query("DELETE FROM MagicLinkToken m WHERE m.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Count recent token requests for rate limiting (tokens created in the last X minutes)
     */
    @Query("SELECT COUNT(m) FROM MagicLinkToken m WHERE m.user = :user AND m.createdAt > :since")
    long countRecentTokensForUser(@Param("user") User user, @Param("since") LocalDateTime since);

    /**
     * Delete all magic link tokens for a user (for user deletion cleanup)
     */
    @Modifying
    @Query("DELETE FROM MagicLinkToken m WHERE m.user = :user")
    void deleteByUser(@Param("user") User user);
}
