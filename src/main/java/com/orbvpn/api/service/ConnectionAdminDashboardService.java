package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.AdminDashboardView;
import com.orbvpn.api.domain.dto.NetworkOverview;
import com.orbvpn.api.domain.dto.ServerStatus;
import com.orbvpn.api.domain.dto.TokenMetrics;
import com.orbvpn.api.domain.dto.TokenActivityPoint;
import com.orbvpn.api.domain.dto.UserActivity;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionAdminDashboardService {
    private final ConnectionStatsRepository connectionStatsRepository;
    private final MiningServerRepository serverRepository;
    private final UserRepository userRepository;
    private final TokenBalanceRepository tokenBalanceRepository;

    @Transactional(readOnly = true)
    public AdminDashboardView getDashboardData() {
        try {
            LocalDateTime dayAgo = LocalDateTime.now().minusDays(1);

            return AdminDashboardView.builder()
                    .networkOverview(buildNetworkOverview())
                    .topServers(getTopServers())
                    .topUsers(getTopUsers())
                    .tokenMetrics(buildTokenMetrics(dayAgo))
                    .build();
        } catch (Exception e) {
            log.error("Error building admin dashboard data", e);
            // Return empty dashboard instead of null to avoid GraphQL errors
            return AdminDashboardView.builder()
                    .networkOverview(NetworkOverview.builder()
                            .totalActiveConnections(0)
                            .totalServers(0)
                            .activeServers(0)
                            .totalDataTransferred(java.math.BigDecimal.ZERO)
                            .averageNetworkUtilization(0.0f)
                            .connectionsByRegion(java.util.Collections.emptyMap())
                            .build())
                    .topServers(java.util.Collections.emptyList())
                    .topUsers(java.util.Collections.emptyList())
                    .tokenMetrics(TokenMetrics.builder()
                            .totalTokensInCirculation(java.math.BigDecimal.ZERO)
                            .totalTokensEarned(java.math.BigDecimal.ZERO)
                            .totalTokensSpent(java.math.BigDecimal.ZERO)
                            .averageDailyVolume(java.math.BigDecimal.ZERO)
                            .recentActivity(java.util.Collections.emptyList())
                            .build())
                    .build();
        }
    }

    private NetworkOverview buildNetworkOverview() {
        List<ConnectionStats> activeConnections = connectionStatsRepository
                .findActiveConnections();

        Map<String, Integer> connectionsByRegion = activeConnections.stream()
                .collect(Collectors.groupingBy(
                        conn -> conn.getServer().getContinent(),
                        Collectors.collectingAndThen(
                                Collectors.counting(),
                                Long::intValue)));

        return NetworkOverview.builder()
                .totalActiveConnections(activeConnections.size())
                .totalServers((int) serverRepository.count())
                .activeServers(serverRepository.countActiveServers())
                .totalDataTransferred(calculateTotalDataTransferred(activeConnections))
                .averageNetworkUtilization(calculateAverageNetworkUtilization(activeConnections))
                .connectionsByRegion(connectionsByRegion)
                .build();
    }

    private List<ServerStatus> getTopServers() {
        return serverRepository.findByMiningEnabledTrue().stream()
                .limit(10)
                .map(server -> {
                    List<ConnectionStats> serverStats = connectionStatsRepository
                            .findByServer(server);

                    return ServerStatus.builder()
                            .serverId(server.getId())
                            .serverName(server.getHostName())
                            .location(server.getLocation())
                            .activeConnections(countActiveConnections(serverStats))
                            .cpuUsage(calculateAverageCpuUsage(serverStats))
                            .memoryUsage(calculateAverageMemoryUsage(serverStats))
                            .networkUtilization(calculateNetworkUtilization(serverStats))
                            .tokenEarnings(calculateTokenEarnings(serverStats))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<UserActivity> getTopUsers() {
        return userRepository.findAllByActiveTrue().stream()
                .limit(10)
                .map(user -> {
                    List<ConnectionStats> userStats = connectionStatsRepository
                            .findByUser(user);

                    return UserActivity.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .activeConnections(countActiveConnections(userStats))
                            .dataTransferred(calculateTotalDataTransferred(userStats))
                            .tokensSpent(calculateTokensSpent(userStats))
                            .lastActive(findLastActive(userStats))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private TokenMetrics buildTokenMetrics(LocalDateTime since) {
        List<TokenActivityPoint> recentActivity = buildTokenActivity(since);

        return TokenMetrics.builder()
                .totalTokensInCirculation(calculateTotalTokensInCirculation())
                .totalTokensEarned(calculateTotalTokensEarned())
                .totalTokensSpent(calculateTotalTokensSpent())
                .averageDailyVolume(calculateAverageDailyVolume(recentActivity))
                .recentActivity(recentActivity)
                .build();
    }

    // Helper methods for calculations...
    private BigDecimal calculateTotalDataTransferred(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getDataTransferred)
                .filter(data -> data != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private float calculateAverageNetworkUtilization(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .mapToDouble(stat -> stat.getNetworkSpeed() != null ? stat.getNetworkSpeed() : 0)
                .average()
                .orElse(0.0);
    }

    private int countActiveConnections(List<ConnectionStats> stats) {
        return (int) stats.stream()
                .filter(stat -> stat.getConnectionEnd() == null)
                .count();
    }

    private float calculateAverageCpuUsage(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .mapToDouble(stat -> stat.getCpuUsage() != null ? stat.getCpuUsage() : 0)
                .average()
                .orElse(0.0);
    }

    private float calculateAverageMemoryUsage(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .mapToDouble(stat -> stat.getMemoryUsage() != null ? stat.getMemoryUsage() : 0)
                .average()
                .orElse(0.0);
    }

    private float calculateNetworkUtilization(List<ConnectionStats> stats) {
        return (float) stats.stream()
                .mapToDouble(stat -> stat.getNetworkSpeed() != null ? stat.getNetworkSpeed() : 0)
                .average()
                .orElse(0.0);
    }

    private BigDecimal calculateTokenEarnings(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getTokensEarned)
                .filter(tokens -> tokens != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTokensSpent(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getTokensCost)
                .filter(tokens -> tokens != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDateTime findLastActive(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getConnectionStart)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private List<TokenActivityPoint> buildTokenActivity(LocalDateTime since) {
        return connectionStatsRepository.findTokenActivitySince(since).stream()
                .map(activity -> TokenActivityPoint.builder()
                        .timestamp(activity.getConnectionStart()) // Using connectionStart as timestamp
                        .earned(activity.getTokensEarned() != null ? activity.getTokensEarned() : BigDecimal.ZERO)
                        .spent(activity.getTokensCost() != null ? activity.getTokensCost() : BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());
    }

    private BigDecimal calculateTotalTokensInCirculation() {
        return tokenBalanceRepository.sumAllBalances();
    }

    private BigDecimal calculateTotalTokensEarned() {
        return connectionStatsRepository.sumTotalTokensEarned();
    }

    private BigDecimal calculateTotalTokensSpent() {
        return connectionStatsRepository.sumTotalTokensCost();
    }

    private BigDecimal calculateAverageDailyVolume(List<TokenActivityPoint> activity) {
        if (activity.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalVolume = activity.stream()
                .map(point -> point.getEarned().add(point.getSpent()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalVolume.divide(BigDecimal.valueOf(activity.size()), 8, RoundingMode.HALF_UP);
    }
}