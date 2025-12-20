package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.ConnectionStatsRepository;
import com.orbvpn.api.repository.MiningServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConnectionStatsService {
    private final ConnectionStatsRepository connectionStatsRepository;
    private final MiningServerRepository miningServerRepository;

    public ConnectionStatsView getCurrentStats(User user) {
        ConnectionStats stats = connectionStatsRepository.findByUserAndConnectionEndIsNull(user)
                .orElse(null);

        if (stats == null) {
            return null;
        }

        return convertToView(stats);
    }

    public Page<ConnectionStatsView> getUserStats(
            User user,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size);
        return connectionStatsRepository
                .findByUserAndConnectionStartBetween(user, from, to, pageable)
                .map(this::convertToView);
    }

    public UserConnectionDashboard getUserDashboard(User user) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        ConnectionStatsView currentConnection = getCurrentStats(user);
        List<ConnectionStats> recentStats = connectionStatsRepository
                .findByUserAndConnectionStartBetween(
                        user,
                        thirtyDaysAgo,
                        LocalDateTime.now(),
                        PageRequest.of(0, 10))
                .getContent();

        ConnectionAverages averages = ConnectionAverages.builder()
                .averageUploadSpeed(connectionStatsRepository.calculateAverageUploadSpeed(user, thirtyDaysAgo))
                .averageDownloadSpeed(connectionStatsRepository.calculateAverageDownloadSpeed(user, thirtyDaysAgo))
                .averageNetworkSpeed(connectionStatsRepository.calculateAverageNetworkSpeed(user, thirtyDaysAgo))
                .averageResponseTime(connectionStatsRepository.calculateAverageResponseTime(user, thirtyDaysAgo))
                .averageLatency(connectionStatsRepository.calculateAverageLatency(user, thirtyDaysAgo))
                .build();

        return UserConnectionDashboard.builder()
                .currentConnection(currentConnection)
                .recentConnections(recentStats.stream().map(this::convertToView).collect(Collectors.toList()))
                .totalDataTransferred(calculateTotalDataTransferred(recentStats))
                .averageMetrics(averages)
                .serverHistory(generateServerHistory(user, thirtyDaysAgo))
                .build();
    }

    public ServerUsageStats getServerUsageStats(User user, Long serverId, LocalDateTime from, LocalDateTime to) {
        MiningServer server = miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        List<ConnectionStats> serverStats = connectionStatsRepository
                .findByUserAndServerBetweenDates(user, server, from, to);

        return generateServerUsageStats(server, serverStats);
    }

    private ConnectionStatsView convertToView(ConnectionStats stats) {
        return ConnectionStatsView.builder()
                .id(stats.getId())
                .serverId(stats.getServer().getId())
                .serverName(stats.getServer().getHostName())
                .connectionStart(stats.getConnectionStart())
                .connectionEnd(stats.getConnectionEnd())
                .dataTransferred(stats.getDataTransferred())
                .cpuUsage(stats.getCpuUsage())
                .memoryUsage(stats.getMemoryUsage())
                .uploadSpeed(stats.getUploadSpeed())
                .downloadSpeed(stats.getDownloadSpeed())
                .networkSpeed(stats.getNetworkSpeed())
                .responseTime(stats.getResponseTime())
                .latency(stats.getLatency())
                .tokensCost(stats.getTokensCost())
                .tokensEarned(stats.getTokensEarned())
                .build();
    }

    private BigDecimal calculateTotalDataTransferred(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getDataTransferred)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ServerUsageStats> generateServerHistory(User user, LocalDateTime since) {
        // Get all mining servers in one query
        List<MiningServer> servers = miningServerRepository.findByMiningEnabledTrue();
        if (servers.isEmpty()) {
            return List.of();
        }

        // Batch query: get all connection stats for user across all servers in one query
        LocalDateTime now = LocalDateTime.now();
        List<ConnectionStats> allStats = connectionStatsRepository
                .findByUserAndServersInDateRange(user, servers, since, now);

        // Group stats by server ID for efficient lookup
        Map<Long, List<ConnectionStats>> statsByServer = allStats.stream()
                .collect(Collectors.groupingBy(cs -> cs.getServer().getId()));

        // Generate usage stats for each server
        return servers.stream()
                .map(server -> {
                    List<ConnectionStats> serverStats = statsByServer.getOrDefault(server.getId(), List.of());
                    return generateServerUsageStats(server, serverStats);
                })
                .filter(stats -> stats.getTotalConnections() > 0)
                .collect(Collectors.toList());
    }

    private ServerUsageStats generateServerUsageStats(MiningServer server, List<ConnectionStats> stats) {
        if (stats.isEmpty()) {
            return ServerUsageStats.builder()
                    .serverId(server.getId())
                    .serverName(server.getHostName())
                    .totalConnections(0)
                    .totalTime(0)
                    .totalDataTransferred(BigDecimal.ZERO)
                    .build();
        }

        int totalMinutes = calculateTotalMinutes(stats);

        ConnectionAverages averages = calculateAverages(stats);

        return ServerUsageStats.builder()
                .serverId(server.getId())
                .serverName(server.getHostName())
                .totalConnections(stats.size())
                .totalTime(totalMinutes)
                .totalDataTransferred(calculateTotalDataTransferred(stats))
                .averagePerformance(averages)
                .lastUsed(stats.get(stats.size() - 1).getConnectionStart())
                .build();
    }

    private int calculateTotalMinutes(List<ConnectionStats> stats) {
        return stats.stream()
                .mapToInt(stat -> {
                    LocalDateTime end = stat.getConnectionEnd() != null ? stat.getConnectionEnd() : LocalDateTime.now();
                    return (int) java.time.Duration.between(
                            stat.getConnectionStart(), end).toMinutes();
                })
                .sum();
    }

    private ConnectionAverages calculateAverages(List<ConnectionStats> stats) {
        return ConnectionAverages.builder()
                .averageUploadSpeed(calculateAverageFloat(stats, ConnectionStats::getUploadSpeed))
                .averageDownloadSpeed(calculateAverageFloat(stats, ConnectionStats::getDownloadSpeed))
                .averageNetworkSpeed(calculateAverageFloat(stats, ConnectionStats::getNetworkSpeed))
                .averageResponseTime(calculateAverageInt(stats, ConnectionStats::getResponseTime))
                .averageLatency(calculateAverageInt(stats, ConnectionStats::getLatency))
                .build();
    }

    private Float calculateAverageFloat(List<ConnectionStats> stats,
            java.util.function.Function<ConnectionStats, Float> getter) {
        return (float) stats.stream()
                .mapToDouble(stat -> getter.apply(stat))
                .average()
                .orElse(0.0);
    }

    private Integer calculateAverageInt(List<ConnectionStats> stats,
            java.util.function.Function<ConnectionStats, Integer> getter) {
        return (int) stats.stream()
                .mapToInt(stat -> getter.apply(stat))
                .average()
                .orElse(0.0);
    }
}