package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.MagicLoginCode;
import com.orbvpn.api.domain.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MagicLoginCodeRepository extends JpaRepository<MagicLoginCode, Long> {

    /**
     * Find the latest valid (not used, not expired) magic login code for a user
     */
    @Query("SELECT m FROM MagicLoginCode m WHERE m.user = :user AND m.used = false AND m.expiresAt > :now ORDER BY m.createdAt DESC LIMIT 1")
    Optional<MagicLoginCode> findLatestValidCodeByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Find a valid magic login code by user and code
     */
    @Query("SELECT m FROM MagicLoginCode m WHERE m.user = :user AND m.code = :code AND m.used = false AND m.expiresAt > :now")
    Optional<MagicLoginCode> findValidCodeByUserAndCode(@Param("user") User user, @Param("code") String code, @Param("now") LocalDateTime now);

    /**
     * Mark all unused codes for a user as used (to invalidate old codes when a new one is generated)
     */
    @Modifying
    @Query("UPDATE MagicLoginCode m SET m.used = true WHERE m.user = :user AND m.used = false")
    int invalidateAllCodesForUser(@Param("user") User user);

    /**
     * Delete expired codes (for cleanup purposes)
     */
    @Modifying
    @Query("DELETE FROM MagicLoginCode m WHERE m.expiresAt < :now")
    int deleteExpiredCodes(@Param("now") LocalDateTime now);

    /**
     * Count recent code requests for rate limiting (codes created in the last X minutes)
     */
    @Query("SELECT COUNT(m) FROM MagicLoginCode m WHERE m.user = :user AND m.createdAt > :since")
    long countRecentCodesForUser(@Param("user") User user, @Param("since") LocalDateTime since);

    /**
     * Delete all magic login codes for a user (for user deletion cleanup)
     */
    @Modifying
    @Query("DELETE FROM MagicLoginCode m WHERE m.user = :user")
    void deleteByUser(@Param("user") User user);
}
