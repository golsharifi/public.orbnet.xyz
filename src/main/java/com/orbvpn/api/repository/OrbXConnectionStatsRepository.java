// src/main/java/com/orbvpn/api/repository/OrbXConnectionStatsRepository.java
package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbXConnectionStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrbXConnectionStatsRepository extends JpaRepository<OrbXConnectionStats, Long> {

    // Find by session ID
    Optional<OrbXConnectionStats> findBySessionId(String sessionId);

    // Find user's sessions with pagination
    Page<OrbXConnectionStats> findByUserIdOrderByConnectedAtDesc(
            Integer userId,
            Pageable pageable);

    // Find user's sessions in date range
    @Query("SELECT s FROM OrbXConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to " +
            "ORDER BY s.connectedAt DESC")
    List<OrbXConnectionStats> findByUserAndDateRange(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Sum bandwidth for user in date range
    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long sumBandwidthByUserAndDateRange(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Count sessions for user in date range
    @Query("SELECT COUNT(s) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Integer countSessionsByUserAndDateRange(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Get average duration for user
    @Query("SELECT COALESCE(AVG(s.duration), 0) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Double getAverageDurationByUser(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Get protocol usage statistics for user
    @Query("SELECT s.protocol, COUNT(s), SUM(s.bytesSent + s.bytesReceived) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to " +
            "GROUP BY s.protocol")
    List<Object[]> getProtocolStatsByUser(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Server statistics
    @Query("SELECT COUNT(DISTINCT s.user.id) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.server.id = :serverId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long countUniqueUsersByServer(
            @Param("serverId") Long serverId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.server.id = :serverId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long sumBandwidthByServer(
            @Param("serverId") Long serverId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Global statistics
    @Query("SELECT COUNT(s) FROM OrbXConnectionStats s " +
            "WHERE s.connectedAt BETWEEN :from AND :to")
    Long countTotalSessions(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.connectedAt BETWEEN :from AND :to")
    Long sumTotalBandwidth(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(DISTINCT s.user.id) " +
            "FROM OrbXConnectionStats s " +
            "WHERE s.connectedAt BETWEEN :from AND :to")
    Long countActiveUsers(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}