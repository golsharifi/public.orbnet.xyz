package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.ConnectionStats;
import com.orbvpn.api.domain.entity.MiningServer;
import com.orbvpn.api.domain.entity.ServerMetrics;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.ConnectionStatsRepository;
import com.orbvpn.api.repository.ServerMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionStatsTrackerService {
    private final ConnectionStatsRepository connectionStatsRepository;
    private final ServerMetricsRepository serverMetricsRepository;

    @Transactional
    public void startTracking(User user, MiningServer server) {
        // End any existing active connections
        ConnectionStats existingConnection = connectionStatsRepository
                .findByUserAndConnectionEndIsNull(user)
                .orElse(null);

        if (existingConnection != null) {
            endTracking(user);
        }

        // Create new connection stats
        ServerMetrics currentMetrics = serverMetricsRepository
                .findFirstByServerOrderByLastCheckDesc(server);

        ConnectionStats stats = ConnectionStats.builder()
                .user(user)
                .server(server)
                .connectionStart(LocalDateTime.now())
                .cpuUsage(currentMetrics.getCpuUsage().floatValue())
                .memoryUsage(currentMetrics.getMemoryUsage().floatValue())
                .uploadSpeed(currentMetrics.getUploadSpeed().floatValue())
                .downloadSpeed(currentMetrics.getDownloadSpeed().floatValue())
                .networkSpeed(currentMetrics.getNetworkSpeed().floatValue())
                .responseTime(currentMetrics.getResponseTime())
                .latency(currentMetrics.getLatency())
                .dataTransferred(BigDecimal.ZERO)
                .build();

        connectionStatsRepository.save(stats);
        log.debug("Started tracking connection stats for user {} on server {}",
                user.getId(), server.getId());
    }

    @Transactional
    public void endTracking(User user) {
        ConnectionStats activeConnection = connectionStatsRepository
                .findByUserAndConnectionEndIsNull(user)
                .orElse(null);

        if (activeConnection != null) {
            ServerMetrics currentMetrics = serverMetricsRepository
                    .findFirstByServerOrderByLastCheckDesc(activeConnection.getServer());

            activeConnection.setConnectionEnd(LocalDateTime.now());
            activeConnection.setCpuUsage(currentMetrics.getCpuUsage().floatValue());
            activeConnection.setMemoryUsage(currentMetrics.getMemoryUsage().floatValue());
            activeConnection.setUploadSpeed(currentMetrics.getUploadSpeed().floatValue());
            activeConnection.setDownloadSpeed(currentMetrics.getDownloadSpeed().floatValue());
            activeConnection.setNetworkSpeed(currentMetrics.getNetworkSpeed().floatValue());
            activeConnection.setResponseTime(currentMetrics.getResponseTime());
            activeConnection.setLatency(currentMetrics.getLatency());

            connectionStatsRepository.save(activeConnection);
            log.debug("Ended tracking connection stats for user {} on server {}",
                    user.getId(), activeConnection.getServer().getId());
        }
    }

    @Scheduled(fixedRate = 60000) // Update every minute
    @Transactional
    public void updateActiveConnectionStats() {
        List<ConnectionStats> activeConnections = connectionStatsRepository
                .findAll()
                .stream()
                .filter(stats -> stats.getConnectionEnd() == null)
                .toList();

        for (ConnectionStats connection : activeConnections) {
            try {
                ServerMetrics currentMetrics = serverMetricsRepository
                        .findFirstByServerOrderByLastCheckDesc(connection.getServer());

                connection.setCpuUsage(currentMetrics.getCpuUsage().floatValue());
                connection.setMemoryUsage(currentMetrics.getMemoryUsage().floatValue());
                connection.setUploadSpeed(currentMetrics.getUploadSpeed().floatValue());
                connection.setDownloadSpeed(currentMetrics.getDownloadSpeed().floatValue());
                connection.setNetworkSpeed(currentMetrics.getNetworkSpeed().floatValue());
                connection.setResponseTime(currentMetrics.getResponseTime());
                connection.setLatency(currentMetrics.getLatency());

                // Update data transferred (you may need to implement this logic based on your
                // needs)
                updateDataTransferred(connection);

                connectionStatsRepository.save(connection);
            } catch (Exception e) {
                log.error("Error updating connection stats for user {} on server {}: {}",
                        connection.getUser().getId(),
                        connection.getServer().getId(),
                        e.getMessage());
            }
        }
    }

    private void updateDataTransferred(ConnectionStats connection) {
        // Implement your logic to calculate data transferred
        // This could involve checking radius accounting data or other metrics
        // For now, this is a placeholder
        BigDecimal currentTransferred = connection.getDataTransferred() != null ? connection.getDataTransferred()
                : BigDecimal.ZERO;
        // Add some sample data transfer (you should replace this with actual metrics)
        connection.setDataTransferred(currentTransferred.add(new BigDecimal("0.1"))); // 100MB per minute
    }
}