// src/main/java/com/orbvpn/api/repository/OrbMeshConnectionStatsRepository.java
package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshConnectionStats;
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
public interface OrbMeshConnectionStatsRepository extends JpaRepository<OrbMeshConnectionStats, Long> {

    // Find by session ID
    Optional<OrbMeshConnectionStats> findBySessionId(String sessionId);

    // Find user's sessions with pagination
    Page<OrbMeshConnectionStats> findByUserIdOrderByConnectedAtDesc(
            Integer userId,
            Pageable pageable);

    // Find user's sessions in date range
    @Query("SELECT s FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to " +
            "ORDER BY s.connectedAt DESC")
    List<OrbMeshConnectionStats> findByUserAndDateRange(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Sum bandwidth for user in date range
    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long sumBandwidthByUserAndDateRange(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Count sessions for user in date range
    @Query("SELECT COUNT(s) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Integer countSessionsByUserAndDateRange(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Get average duration for user
    @Query("SELECT COALESCE(AVG(s.duration), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Double getAverageDurationByUser(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Get protocol usage statistics for user
    @Query("SELECT s.protocol, COUNT(s), SUM(s.bytesSent + s.bytesReceived) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to " +
            "GROUP BY s.protocol")
    List<Object[]> getProtocolStatsByUser(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Server statistics
    @Query("SELECT COUNT(DISTINCT s.user.id) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.server.id = :serverId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long countUniqueUsersByServer(
            @Param("serverId") Long serverId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.server.id = :serverId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long sumBandwidthByServer(
            @Param("serverId") Long serverId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // Global statistics
    @Query("SELECT COUNT(s) FROM OrbMeshConnectionStats s " +
            "WHERE s.connectedAt BETWEEN :from AND :to")
    Long countTotalSessions(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.connectedAt BETWEEN :from AND :to")
    Long sumTotalBandwidth(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(DISTINCT s.user.id) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.connectedAt BETWEEN :from AND :to")
    Long countActiveUsers(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ========== ACTIVE CONNECTION TRACKING ==========

    /**
     * Count active (not disconnected) connections for a user.
     * Active = disconnectedAt IS NULL
     */
    @Query("SELECT COUNT(s) FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.disconnectedAt IS NULL")
    int countActiveConnectionsByUserId(@Param("userId") Integer userId);

    /**
     * Count active connections for a user on a specific protocol (wireguard, vless).
     */
    @Query("SELECT COUNT(s) FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.protocol = :protocol " +
            "AND s.disconnectedAt IS NULL")
    int countActiveConnectionsByUserIdAndProtocol(
            @Param("userId") Integer userId,
            @Param("protocol") String protocol);

    /**
     * Find all active connections for a user (for disconnection if needed).
     */
    @Query("SELECT s FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.disconnectedAt IS NULL " +
            "ORDER BY s.connectedAt ASC")
    List<OrbMeshConnectionStats> findActiveConnectionsByUserId(@Param("userId") Integer userId);

    /**
     * Find oldest active connection for a user (for FIFO disconnection).
     */
    @Query("SELECT s FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.disconnectedAt IS NULL " +
            "ORDER BY s.connectedAt ASC")
    List<OrbMeshConnectionStats> findOldestActiveConnectionByUserId(@Param("userId") Integer userId);

    // ========== BANDWIDTH BY VPN PROTOCOL ==========

    /**
     * Sum bandwidth by user and VPN protocol (wireguard, vless) in date range.
     */
    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.vpnProtocol = :vpnProtocol " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long sumBandwidthByUserAndProtocol(
            @Param("userId") Integer userId,
            @Param("vpnProtocol") String vpnProtocol,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Get bandwidth usage grouped by VPN protocol for a user.
     */
    @Query("SELECT s.vpnProtocol, SUM(s.bytesSent + s.bytesReceived) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to " +
            "GROUP BY s.vpnProtocol")
    List<Object[]> getBandwidthByVpnProtocol(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Sum total bandwidth for user in date range (all protocols).
     */
    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.user.id = :userId " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long sumTotalBandwidthByUser(
            @Param("userId") Integer userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ========== ADMIN GLOBAL BANDWIDTH QUERIES ==========

    /**
     * Sum total bandwidth in period (for admin reports).
     */
    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.connectedAt BETWEEN :from AND :to")
    Long sumTotalBandwidthInPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Sum bandwidth by VPN protocol in period (for admin reports).
     */
    @Query("SELECT COALESCE(SUM(s.bytesSent + s.bytesReceived), 0) " +
            "FROM OrbMeshConnectionStats s " +
            "WHERE s.vpnProtocol = :vpnProtocol " +
            "AND s.connectedAt BETWEEN :from AND :to")
    Long sumBandwidthByVpnProtocolInPeriod(
            @Param("vpnProtocol") String vpnProtocol,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Find all active connections (admin view).
     */
    @Query("SELECT s FROM OrbMeshConnectionStats s " +
            "WHERE s.disconnectedAt IS NULL " +
            "ORDER BY s.connectedAt DESC")
    List<OrbMeshConnectionStats> findAllActiveConnections();

    /**
     * Count all active connections.
     */
    @Query("SELECT COUNT(s) FROM OrbMeshConnectionStats s " +
            "WHERE s.disconnectedAt IS NULL")
    long countAllActiveConnections();
}