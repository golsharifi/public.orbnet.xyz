package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.AdViewingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdViewingSessionRepository extends JpaRepository<AdViewingSession, Long> {

    /**
     * Find a session by its unique session ID.
     */
    Optional<AdViewingSession> findBySessionId(String sessionId);

    /**
     * Check if a session exists and is pending.
     */
    @Query("SELECT COUNT(s) > 0 FROM AdViewingSession s WHERE s.sessionId = :sessionId AND s.status = 'PENDING'")
    boolean existsPendingSession(@Param("sessionId") String sessionId);

    /**
     * Count pending sessions for a user (prevent abuse).
     */
    @Query("SELECT COUNT(s) FROM AdViewingSession s WHERE s.user.id = :userId AND s.status = 'PENDING' AND s.expiresAt > :now")
    long countPendingSessionsForUser(@Param("userId") Integer userId, @Param("now") LocalDateTime now);

    /**
     * Count recent sessions from same IP (rate limiting).
     */
    @Query("SELECT COUNT(s) FROM AdViewingSession s WHERE s.ipAddress = :ip AND s.createdAt > :since")
    long countRecentSessionsFromIp(@Param("ip") String ip, @Param("since") LocalDateTime since);

    /**
     * Count recent sessions from same device (rate limiting).
     */
    @Query("SELECT COUNT(s) FROM AdViewingSession s WHERE s.deviceId = :deviceId AND s.createdAt > :since")
    long countRecentSessionsFromDevice(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);

    /**
     * Mark expired sessions.
     */
    @Modifying
    @Query("UPDATE AdViewingSession s SET s.status = 'EXPIRED' WHERE s.status = 'PENDING' AND s.expiresAt < :now")
    int markExpiredSessions(@Param("now") LocalDateTime now);

    /**
     * Find sessions for a user (for audit/monitoring).
     */
    List<AdViewingSession> findByUserIdOrderByCreatedAtDesc(Integer userId);

    /**
     * Count completed sessions for a user in a time range.
     */
    @Query("SELECT COUNT(s) FROM AdViewingSession s WHERE s.user.id = :userId AND s.status = 'COMPLETED' AND s.completedAt BETWEEN :start AND :end")
    int countCompletedSessionsInRange(@Param("userId") Integer userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Delete old sessions (cleanup).
     */
    @Modifying
    @Query("DELETE FROM AdViewingSession s WHERE s.createdAt < :cutoff")
    int deleteOldSessions(@Param("cutoff") LocalDateTime cutoff);
}
