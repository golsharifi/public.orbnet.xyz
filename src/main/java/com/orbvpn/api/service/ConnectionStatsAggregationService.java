package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionStatsAggregationService {
    private final ConnectionStatsRepository connectionStatsRepository;
    private final ConnectionStatsAggregateRepository aggregateRepository;
    private final UserRepository userRepository;
    private final MiningServerRepository serverRepository;

    @Scheduled(cron = "0 5 * * * *") // 5 minutes past every hour
    @Transactional
    public void aggregateHourlyStats() {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime startTime = endTime.minusHours(1);

        aggregateStats(startTime, endTime, ConnectionStatsAggregate.AggregationPeriod.HOURLY);
    }

    @Scheduled(cron = "0 15 0 * * *") // 00:15 every day
    @Transactional
    public void aggregateDailyStats() {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime startTime = endTime.minusDays(1);

        aggregateStats(startTime, endTime, ConnectionStatsAggregate.AggregationPeriod.DAILY);
    }

    @Scheduled(cron = "0 30 0 1 * *") // 00:30 on the 1st of every month
    @Transactional
    public void aggregateMonthlyStats() {
        LocalDateTime endTime = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)
                .withDayOfMonth(1);
        LocalDateTime startTime = endTime.minusMonths(1);

        aggregateStats(startTime, endTime, ConnectionStatsAggregate.AggregationPeriod.MONTHLY);
    }

    private void aggregateStats(
            LocalDateTime startTime,
            LocalDateTime endTime,
            ConnectionStatsAggregate.AggregationPeriod period) {

        try {
            List<ConnectionStats> statsInPeriod = connectionStatsRepository.findAll().stream()
                    .filter(stats -> isWithinPeriod(stats, startTime, endTime))
                    .toList();

            // Aggregate by user and server
            Map<UserServerKey, List<ConnectionStats>> groupedStats = statsInPeriod.stream()
                    .collect(Collectors.groupingBy(
                            stats -> new UserServerKey(stats.getUser().getId(), stats.getServer().getId())));

            for (Map.Entry<UserServerKey, List<ConnectionStats>> entry : groupedStats.entrySet()) {
                createAggregation(entry.getKey(), entry.getValue(), endTime, period);
            }

        } catch (Exception e) {
            log.error("Error during {} stats aggregation: {}", period, e.getMessage(), e);
        }
    }

    private record UserServerKey(Integer userId, Long serverId) {
    }

    private boolean isWithinPeriod(ConnectionStats stats, LocalDateTime start, LocalDateTime end) {
        return (stats.getConnectionStart().isAfter(start) || stats.getConnectionStart().equals(start))
                && stats.getConnectionStart().isBefore(end);
    }

    private void createAggregation(
            UserServerKey key,
            List<ConnectionStats> stats,
            LocalDateTime aggregationDate,
            ConnectionStatsAggregate.AggregationPeriod period) {

        User user = userRepository.findById(key.userId())
                .orElseThrow(() -> new RuntimeException("User not found: " + key.userId()));
        MiningServer server = serverRepository.findById(key.serverId())
                .orElseThrow(() -> new RuntimeException("Server not found: " + key.serverId()));

        ConnectionStatsAggregate aggregate = ConnectionStatsAggregate.builder()
                .user(user)
                .server(server)
                .aggregationDate(aggregationDate)
                .period(period)
                .totalDataTransferred(calculateTotalDataTransferred(stats))
                .totalConnections(stats.size())
                .totalMinutes(calculateTotalMinutes(stats))
                .averageCpuUsage(calculateAverageFloat(stats, ConnectionStats::getCpuUsage))
                .averageMemoryUsage(calculateAverageFloat(stats, ConnectionStats::getMemoryUsage))
                .averageNetworkSpeed(calculateAverageFloat(stats, ConnectionStats::getNetworkSpeed))
                .averageResponseTime(calculateAverageInt(stats, ConnectionStats::getResponseTime))
                .averageLatency(calculateAverageInt(stats, ConnectionStats::getLatency))
                .totalTokensCost(calculateTotalTokens(stats, ConnectionStats::getTokensCost))
                .totalTokensEarned(calculateTotalTokens(stats, ConnectionStats::getTokensEarned))
                .build();

        aggregateRepository.save(aggregate);
    }

    private BigDecimal calculateTotalDataTransferred(List<ConnectionStats> stats) {
        return stats.stream()
                .map(ConnectionStats::getDataTransferred)
                .filter(data -> data != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int calculateTotalMinutes(List<ConnectionStats> stats) {
        return stats.stream()
                .mapToInt(stat -> {
                    LocalDateTime end = stat.getConnectionEnd() != null ? stat.getConnectionEnd() : LocalDateTime.now();
                    return (int) ChronoUnit.MINUTES.between(stat.getConnectionStart(), end);
                })
                .sum();
    }

    private Float calculateAverageFloat(
            List<ConnectionStats> stats,
            java.util.function.Function<ConnectionStats, Float> getter) {
        return (float) stats.stream()
                .map(getter)
                .filter(value -> value != null)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);
    }

    private Integer calculateAverageInt(
            List<ConnectionStats> stats,
            java.util.function.Function<ConnectionStats, Integer> getter) {
        return (int) stats.stream()
                .map(getter)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    private BigDecimal calculateTotalTokens(
            List<ConnectionStats> stats,
            java.util.function.Function<ConnectionStats, BigDecimal> getter) {
        return stats.stream()
                .map(getter)
                .filter(tokens -> tokens != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}