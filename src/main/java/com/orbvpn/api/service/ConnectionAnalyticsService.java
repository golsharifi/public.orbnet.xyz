package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.NetworkAnalytics;
import com.orbvpn.api.domain.dto.ServerPerformanceMetrics;
import com.orbvpn.api.domain.dto.UserActivityMetrics;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.repository.ConnectionStatsRepository;
import com.orbvpn.api.repository.MiningServerRepository;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class ConnectionAnalyticsService {
    private final ConnectionStatsRepository connectionStatsRepository;
    private final MiningServerRepository miningServerRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public NetworkAnalytics getNetworkAnalytics(LocalDateTime from, LocalDateTime to) {
        List<ConnectionStats> activeConnections = connectionStatsRepository.findAll().stream()
                .filter(stats -> stats.getConnectionEnd() == null)
                .toList();

        Map<String, List<ConnectionStats>> connectionsPerServer = activeConnections.stream()
                .collect(Collectors.groupingBy(stats -> stats.getServer().getHostName()));

        List<ServerPerformanceMetrics> serverMetrics = miningServerRepository.findByMiningEnabledTrue().stream()
                .map(server -> calculateServerPerformance(server, from, to))
                .sorted((a, b) -> b.getTotalDataTransferred().compareTo(a.getTotalDataTransferred()))
                .limit(10)
                .toList();

        List<UserActivityMetrics> userMetrics = userRepository.findAllByActiveTrue().stream()
                .map(user -> calculateUserActivity(user, from, to))
                .sorted((a, b) -> b.getTotalDataTransferred().compareTo(a.getTotalDataTransferred()))
                .limit(10)
                .toList();

        return NetworkAnalytics.builder()
                .totalActiveConnections(activeConnections.size())
                .totalUsers((int) userRepository.count())
                .totalServers((int) miningServerRepository.count())
                .totalDataTransferred(calculateTotalDataTransferred(from, to))
                .connectionsPerServer(connectionsPerServer.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().size())))
                .dataPerServer(calculateDataPerServer(from, to))
                .topPerformingServers(serverMetrics)
                .mostActiveUsers(userMetrics)
                .build();
    }

    private ServerPerformanceMetrics calculateServerPerformance(
            MiningServer server,
            LocalDateTime from,
            LocalDateTime to) {
        List<ConnectionStats> serverStats = connectionStatsRepository.findAll().stream()
                .filter(stats -> stats.getServer().getId().equals(server.getId()))
                .filter(stats -> stats.getConnectionStart().isAfter(from))
                .filter(stats -> stats.getConnectionEnd() == null || stats.getConnectionEnd().isBefore(to))
                .toList();

        return ServerPerformanceMetrics.builder()
                .serverId(server.getId())
                .serverName(server.getHostName())
                .averageCpuUsage(calculateAverageCpuUsage(serverStats))
                .averageMemoryUsage(calculateAverageMemoryUsage(serverStats))
                .averageNetworkSpeed(calculateAverageNetworkSpeed(serverStats))
                .activeConnections((int) serverStats.stream()
                        .filter(stats -> stats.getConnectionEnd() == null)
                        .count())
                .totalDataTransferred(calculateServerDataTransferred(serverStats))
                .build();
    }

    private boolean isSameUser(ConnectionStats stats, User user) {
        if (stats == null || stats.getUser() == null || user == null) {
            return false;
        }

        Integer statsUserId = stats.getUser().getId();
        Integer userId = user.getId();

        if (statsUserId == null || userId == null) {
            return false;
        }

        return statsUserId.equals(userId);
    }

    private UserActivityMetrics calculateUserActivity(User user, LocalDateTime from, LocalDateTime to) {
        List<ConnectionStats> userStats = connectionStatsRepository.findAll().stream()
                .filter(stats -> isSameUser(stats, user))
                .filter(stats -> stats.getConnectionStart().isAfter(from))
                .filter(stats -> stats.getConnectionEnd() == null || stats.getConnectionEnd().isBefore(to))
                .toList();

        return UserActivityMetrics.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .totalConnections(userStats.size())
                .activeConnections((int) userStats.stream()
                        .filter(stats -> stats.getConnectionEnd() == null)
                        .count())
                .totalDataTransferred(calculateUserDataTransferred(userStats))
                .averageSessionDuration(calculateAverageSessionDuration(userStats))
                .build();
    }

    private Float calculateAverageCpuUsage(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .map(ConnectionStats::getCpuUsage)
                .filter(cpu -> cpu != null)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);
    }

    private Float calculateAverageMemoryUsage(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .map(ConnectionStats::getMemoryUsage)
                .filter(mem -> mem != null)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);
    }

    private Float calculateAverageNetworkSpeed(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .map(ConnectionStats::getNetworkSpeed)
                .filter(speed -> speed != null)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);
    }

    private BigDecimal calculateTotalDataTransferred(LocalDateTime from, LocalDateTime to) {
        return connectionStatsRepository.findAll().stream()
                .filter(stats -> stats.getConnectionStart().isAfter(from))
                .filter(stats -> stats.getConnectionEnd() == null || stats.getConnectionEnd().isBefore(to))
                .map(ConnectionStats::getDataTransferred)
                .filter(data -> data != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> calculateDataPerServer(LocalDateTime from, LocalDateTime to) {
        return connectionStatsRepository.findAll().stream()
                .filter(stats -> stats.getConnectionStart().isAfter(from))
                .filter(stats -> stats.getConnectionEnd() == null || stats.getConnectionEnd().isBefore(to))
                .collect(Collectors.groupingBy(
                        stats -> stats.getServer().getHostName(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                stats -> stats.getDataTransferred() != null ? stats.getDataTransferred()
                                        : BigDecimal.ZERO,
                                BigDecimal::add)));
    }

    private BigDecimal calculateServerDataTransferred(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getDataTransferred)
                .filter(data -> data != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateUserDataTransferred(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getDataTransferred)
                .filter(data -> data != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Float calculateAverageSessionDuration(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .mapToLong(stat -> {
                    LocalDateTime end = stat.getConnectionEnd() != null ? stat.getConnectionEnd() : LocalDateTime.now();
                    return java.time.Duration.between(stat.getConnectionStart(), end).toMinutes();
                })
                .average()
                .orElse(0.0);
    }
}
