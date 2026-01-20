package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    /**
     * Check if a token is revoked by its JTI (hash).
     */
    boolean existsByJti(String jti);

    /**
     * Find all revoked tokens for a user.
     */
    List<RevokedToken> findByUserId(Integer userId);

    /**
     * Count revoked tokens for a user (for audit/monitoring).
     */
    long countByUserId(Integer userId);

    /**
     * Delete expired tokens (cleanup job).
     * Tokens that have naturally expired can be safely removed.
     */
    @Modifying
    @Query("DELETE FROM RevokedToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Delete all tokens for a user (when account is deleted).
     */
    @Modifying
    @Query("DELETE FROM RevokedToken rt WHERE rt.userId = :userId")
    int deleteByUserId(@Param("userId") Integer userId);

    /**
     * Find tokens revoked within a time range (for audit).
     */
    @Query("SELECT rt FROM RevokedToken rt WHERE rt.revokedAt BETWEEN :start AND :end ORDER BY rt.revokedAt DESC")
    List<RevokedToken> findRevokedInRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
