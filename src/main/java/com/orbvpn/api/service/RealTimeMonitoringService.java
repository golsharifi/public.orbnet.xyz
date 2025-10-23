package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.domain.entity.ServerMetrics;
import com.orbvpn.api.domain.entity.MiningServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeMonitoringService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ConnectionStatsRepository connectionStatsRepository;
    private final MiningServerRepository serverRepository;
    private final ServerMetricsRepository serverMetricsRepository;

    private final Map<Long, ServerMetrics> lastServerMetrics = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 5000) // Every 5 seconds
    public void broadcastMetrics() {
        try {
            RealTimeMetrics metrics = collectRealTimeMetrics();
            messagingTemplate.convertAndSend("/topic/metrics", metrics);
        } catch (Exception e) {
            log.error("Error broadcasting metrics: {}", e.getMessage(), e);
        }
    }

    private RealTimeMetrics collectRealTimeMetrics() {
        Map<String, ServerRealTimeMetrics> serverMetrics = new ConcurrentHashMap<>();

        serverRepository.findByMiningEnabledTrue().forEach(server -> {
            com.orbvpn.api.domain.entity.ServerMetrics currentMetrics = serverMetricsRepository
                    .findFirstByServerOrderByLastCheckDesc(server);

            if (currentMetrics != null) {
                ServerMetrics previousMetrics = lastServerMetrics.get(server.getId());
                serverMetrics.put(server.getHostName(), buildServerMetrics(server, currentMetrics, previousMetrics));
                lastServerMetrics.put(server.getId(), currentMetrics);
            }
        });

        return RealTimeMetrics.builder()
                .timestamp(LocalDateTime.now())
                .activeConnections(connectionStatsRepository.countActiveConnections())
                .serverMetrics(serverMetrics)
                .networkMetrics(buildNetworkMetrics())
                .build();
    }

    private ServerRealTimeMetrics buildServerMetrics(
            MiningServer server,
            ServerMetrics currentMetrics,
            ServerMetrics previousMetrics) {

        return ServerRealTimeMetrics.builder()
                .serverId(server.getId())
                .serverName(server.getHostName())
                .activeConnections(currentMetrics.getActiveConnections())
                .cpuUsage(currentMetrics.getCpuUsage().floatValue())
                .memoryUsage(currentMetrics.getMemoryUsage().floatValue())
                .networkUtilization(currentMetrics.getNetworkSpeed().floatValue())
                .trend(calculateTrend(currentMetrics, previousMetrics))
                .build();
    }

    private MetricsTrend calculateTrend(ServerMetrics current, ServerMetrics previous) {
        if (previous == null) {
            return MetricsTrend.builder()
                    .cpuImproving(false)
                    .memoryImproving(false)
                    .networkImproving(false)
                    .build();
        }

        return MetricsTrend.builder()
                .cpuImproving(current.getCpuUsage().compareTo(previous.getCpuUsage()) < 0)
                .memoryImproving(current.getMemoryUsage().compareTo(previous.getMemoryUsage()) < 0)
                .networkImproving(current.getNetworkSpeed().compareTo(previous.getNetworkSpeed()) > 0)
                .build();
    }

    private NetworkMetrics buildNetworkMetrics() {
        List<ServerMetrics> allMetrics = serverMetricsRepository.findLatestMetrics();

        return NetworkMetrics.builder()
                .totalBandwidth(calculateTotalBandwidth(allMetrics))
                .averageLatency(calculateAverageLatency(allMetrics))
                .totalActiveUsers(connectionStatsRepository.countDistinctActiveUsers())
                .connectionsByRegion(calculateConnectionsByRegion())
                .currentTokenRate(calculateCurrentTokenRate())
                .build();
    }

    private float calculateTotalBandwidth(List<ServerMetrics> metrics) {
        return metrics.stream()
                .map(ServerMetrics::getNetworkSpeed)
                .filter(speed -> speed != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .floatValue();
    }

    private float calculateAverageLatency(List<ServerMetrics> metrics) {
        return (float) metrics.stream()
                .mapToInt(ServerMetrics::getLatency)
                .average()
                .orElse(0.0);
    }

    private Map<String, Integer> calculateConnectionsByRegion() {
        return connectionStatsRepository.countActiveConnectionsByRegion();
    }

    private BigDecimal calculateCurrentTokenRate() {
        // Implement token rate calculation based on your business logic
        return BigDecimal.ONE; // Placeholder
    }
}