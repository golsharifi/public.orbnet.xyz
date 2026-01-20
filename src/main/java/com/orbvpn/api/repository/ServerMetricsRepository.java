package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.ServerMetrics;
import com.orbvpn.api.domain.entity.MiningServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

public interface ServerMetricsRepository extends JpaRepository<ServerMetrics, Long> {
    ServerMetrics findFirstByServerOrderByLastCheckDesc(MiningServer server);

    @Query("SELECT AVG(m.uptime) FROM ServerMetrics m " +
            "WHERE m.server = :server AND m.lastCheck >= :since")
    BigDecimal calculateAverageUptime(@Param("server") MiningServer server,
            @Param("since") LocalDateTime since);

    @Query("SELECT AVG(m.responseTime) FROM ServerMetrics m " +
            "WHERE m.server = :server AND m.lastCheck >= :since")
    Double calculateAverageResponseTime(@Param("server") MiningServer server,
            @Param("since") LocalDateTime since);

    List<ServerMetrics> findByServerAndLastCheckBetweenOrderByLastCheckDesc(
            MiningServer server,
            LocalDateTime startDate,
            LocalDateTime endDate);

    List<ServerMetrics> findByServerOrderByLastCheckDesc(MiningServer server);

    List<ServerMetrics> findTop24ByServerOrderByLastCheckDesc(MiningServer server);

    @Query("SELECT AVG(m.cpuUsage) FROM ServerMetrics m WHERE m.server = :server AND m.lastCheck >= :since")
    BigDecimal getAverageCpuUsage(@Param("server") MiningServer server, @Param("since") LocalDateTime since);

    @Query("SELECT AVG(m.memoryUsage) FROM ServerMetrics m WHERE m.server = :server AND m.lastCheck >= :since")
    BigDecimal getAverageMemoryUsage(@Param("server") MiningServer server, @Param("since") LocalDateTime since);

    @Query("SELECT m FROM ServerMetrics m WHERE m.lastCheck = " +
            "(SELECT MAX(m2.lastCheck) FROM ServerMetrics m2 WHERE m2.server = m.server)")
    List<ServerMetrics> findLatestMetrics();
}
