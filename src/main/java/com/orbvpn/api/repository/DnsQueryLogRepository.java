package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.DnsQueryLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DnsQueryLogRepository extends JpaRepository<DnsQueryLog, Long> {

    @Query("SELECT l FROM DnsQueryLog l WHERE l.userId = :userId ORDER BY l.timestamp DESC")
    List<DnsQueryLog> findByUserIdOrderByTimestampDesc(@Param("userId") int userId, Pageable pageable);

    @Query("SELECT l FROM DnsQueryLog l ORDER BY l.timestamp DESC")
    List<DnsQueryLog> findAllOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT l FROM DnsQueryLog l WHERE l.serviceId = :serviceId ORDER BY l.timestamp DESC")
    List<DnsQueryLog> findByServiceIdOrderByTimestampDesc(@Param("serviceId") String serviceId, Pageable pageable);

    @Query("SELECT l FROM DnsQueryLog l WHERE l.region = :region ORDER BY l.timestamp DESC")
    List<DnsQueryLog> findByRegionOrderByTimestampDesc(@Param("region") String region, Pageable pageable);

    // Stats queries
    @Query("SELECT COUNT(l) FROM DnsQueryLog l")
    long countAll();

    @Query("SELECT COUNT(l) FROM DnsQueryLog l WHERE l.userId = :userId")
    long countByUserId(@Param("userId") int userId);

    @Query("SELECT COUNT(l) FROM DnsQueryLog l WHERE l.responseType = 'PROXIED'")
    long countProxied();

    @Query("SELECT COUNT(l) FROM DnsQueryLog l WHERE l.userId = :userId AND l.responseType = 'PROXIED'")
    long countProxiedByUserId(@Param("userId") int userId);

    @Query("SELECT COUNT(l) FROM DnsQueryLog l WHERE l.responseType = 'BLOCKED'")
    long countBlocked();

    @Query("SELECT COUNT(l) FROM DnsQueryLog l WHERE l.responseType = 'CACHED'")
    long countCached();

    @Query("SELECT AVG(l.latencyMs) FROM DnsQueryLog l WHERE l.latencyMs IS NOT NULL")
    Double avgLatency();

    @Query("SELECT COUNT(l) FROM DnsQueryLog l WHERE l.timestamp >= :since")
    long countSince(@Param("since") LocalDateTime since);

    // Service stats
    @Query("SELECT l.serviceId, COUNT(l) FROM DnsQueryLog l WHERE l.serviceId IS NOT NULL GROUP BY l.serviceId ORDER BY COUNT(l) DESC")
    List<Object[]> findServiceStats(Pageable pageable);

    @Query("SELECT l.serviceId, COUNT(l) FROM DnsQueryLog l WHERE l.userId = :userId AND l.serviceId IS NOT NULL GROUP BY l.serviceId ORDER BY COUNT(l) DESC")
    List<Object[]> findServiceStatsByUserId(@Param("userId") int userId, Pageable pageable);

    // Region stats
    @Query("SELECT l.region, COUNT(l) FROM DnsQueryLog l WHERE l.region IS NOT NULL GROUP BY l.region ORDER BY COUNT(l) DESC")
    List<Object[]> findRegionStats();

    // Hourly stats (last 24 hours)
    @Query("SELECT HOUR(l.timestamp), COUNT(l) FROM DnsQueryLog l WHERE l.timestamp >= :since GROUP BY HOUR(l.timestamp) ORDER BY HOUR(l.timestamp)")
    List<Object[]> findHourlyStats(@Param("since") LocalDateTime since);

    @Query("SELECT HOUR(l.timestamp), COUNT(l) FROM DnsQueryLog l WHERE l.userId = :userId AND l.timestamp >= :since GROUP BY HOUR(l.timestamp) ORDER BY HOUR(l.timestamp)")
    List<Object[]> findHourlyStatsByUserId(@Param("userId") int userId, @Param("since") LocalDateTime since);

    // Last activity
    @Query("SELECT MAX(l.timestamp) FROM DnsQueryLog l WHERE l.userId = :userId")
    LocalDateTime findLastActivityByUserId(@Param("userId") int userId);

    // Cleanup
    @Modifying
    @Query("DELETE FROM DnsQueryLog l WHERE l.timestamp < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
