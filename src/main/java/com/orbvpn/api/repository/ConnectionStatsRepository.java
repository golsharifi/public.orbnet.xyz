package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ConnectionStats;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.MiningServer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface ConnectionStatsRepository extends JpaRepository<ConnectionStats, Long> {

    Optional<ConnectionStats> findByUserAndConnectionEndIsNull(User user);

    Page<ConnectionStats> findByUserAndConnectionStartBetween(
            User user,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable);

    @Query("SELECT cs FROM ConnectionStats cs " +
            "WHERE cs.user = :user " +
            "AND cs.server = :server " +
            "AND cs.connectionStart BETWEEN :start AND :end")
    List<ConnectionStats> findByUserAndServerBetweenDates(
            @Param("user") User user,
            @Param("server") MiningServer server,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT AVG(cs.uploadSpeed) FROM ConnectionStats cs " +
            "WHERE cs.user = :user AND cs.connectionStart >= :since")
    Float calculateAverageUploadSpeed(
            @Param("user") User user,
            @Param("since") LocalDateTime since);

    @Query("SELECT AVG(cs.downloadSpeed) FROM ConnectionStats cs " +
            "WHERE cs.user = :user AND cs.connectionStart >= :since")
    Float calculateAverageDownloadSpeed(
            @Param("user") User user,
            @Param("since") LocalDateTime since);

    @Query("SELECT AVG(cs.networkSpeed) FROM ConnectionStats cs " +
            "WHERE cs.user = :user AND cs.connectionStart >= :since")
    Float calculateAverageNetworkSpeed(
            @Param("user") User user,
            @Param("since") LocalDateTime since);

    @Query("SELECT AVG(cs.responseTime) FROM ConnectionStats cs " +
            "WHERE cs.user = :user AND cs.connectionStart >= :since")
    Integer calculateAverageResponseTime(
            @Param("user") User user,
            @Param("since") LocalDateTime since);

    @Query("SELECT AVG(cs.latency) FROM ConnectionStats cs " +
            "WHERE cs.user = :user AND cs.connectionStart >= :since")
    Integer calculateAverageLatency(
            @Param("user") User user,
            @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM ConnectionStats cs WHERE cs.connectionEnd < :cutoffDate")
    int deleteByConnectionEndBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Add to ConnectionStatsRepository.java

    @Query("SELECT COUNT(DISTINCT c) FROM ConnectionStats c WHERE c.connectionEnd IS NULL")
    int countActiveConnections();

    @Query("SELECT COUNT(DISTINCT c.user) FROM ConnectionStats c WHERE c.connectionEnd IS NULL")
    int countDistinctActiveUsers();

    @Query("SELECT NEW map(s.continent as region, COUNT(c) as count) " +
            "FROM ConnectionStats c " +
            "JOIN c.server s " +
            "WHERE c.connectionEnd IS NULL " +
            "GROUP BY s.continent")
    Map<String, Integer> countActiveConnectionsByRegion();

    @Query("SELECT cs FROM ConnectionStats cs WHERE cs.user.id = :userId " +
            "AND cs.connectionStart >= :startDate AND cs.connectionEnd <= :endDate")
    List<ConnectionStats> findByUserIdAndPeriod(
            @Param("userId") Integer userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT cs FROM ConnectionStats cs " +
            "WHERE cs.connectionStart >= :startDate AND cs.connectionEnd <= :endDate")
    List<ConnectionStats> findByPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Find active connections (where connectionEnd is null)
    @Query("SELECT c FROM ConnectionStats c WHERE c.connectionEnd IS NULL")
    List<ConnectionStats> findActiveConnections();

    // Find connections by server
    @Query("SELECT c FROM ConnectionStats c WHERE c.server = :server")
    List<ConnectionStats> findByServer(@Param("server") MiningServer server);

    // Find token activity since a specific date
    @Query("SELECT c FROM ConnectionStats c WHERE c.connectionStart >= :since")
    List<ConnectionStats> findTokenActivitySince(@Param("since") LocalDateTime since);

    // Sum total tokens earned
    @Query("SELECT COALESCE(SUM(c.tokensEarned), 0) FROM ConnectionStats c")
    BigDecimal sumTotalTokensEarned();

    // Sum total tokens cost
    @Query("SELECT COALESCE(SUM(c.tokensCost), 0) FROM ConnectionStats c")
    BigDecimal sumTotalTokensCost();

    // Find by user
    @Query("SELECT c FROM ConnectionStats c WHERE c.user = :user")
    List<ConnectionStats> findByUser(@Param("user") User user);

    /**
     * Batch query to get connection stats for a user across multiple servers.
     * Eliminates N+1 query problem in dashboard generation.
     */
    @Query("SELECT cs FROM ConnectionStats cs " +
            "WHERE cs.user = :user " +
            "AND cs.server IN :servers " +
            "AND cs.connectionStart BETWEEN :start AND :end")
    List<ConnectionStats> findByUserAndServersInDateRange(
            @Param("user") User user,
            @Param("servers") List<MiningServer> servers,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
