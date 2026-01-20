package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.HistoricalStatsView;
import com.orbvpn.api.domain.dto.HistoricalStatsView.TimeSeriesPoint;
import com.orbvpn.api.domain.entity.MiningServer;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.ConnectionStatsAggregate;
import com.orbvpn.api.repository.ConnectionStatsAggregateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalStatsService {
    private final ConnectionStatsAggregateRepository aggregateRepository;
    private final MiningServerService miningServerService;

    @Transactional(readOnly = true)
    public HistoricalStatsView getUserHistoricalStats(
            User user,
            ConnectionStatsAggregate.AggregationPeriod period,
            LocalDateTime from,
            LocalDateTime to) {

        List<ConnectionStatsAggregate> aggregates = aggregateRepository
                .findByUserAndPeriodAndAggregationDateBetween(user, period, from, to);

        return buildHistoricalStatsView(aggregates);
    }

    @Transactional(readOnly = true)
    public HistoricalStatsView getServerHistoricalStats(
            Long serverId,
            ConnectionStatsAggregate.AggregationPeriod period,
            LocalDateTime from,
            LocalDateTime to) {

        MiningServer server = miningServerService.getServerById(serverId);
        List<ConnectionStatsAggregate> aggregates = aggregateRepository
                .findByServerAndPeriodAndAggregationDateBetween(server, period, from, to);

        return buildHistoricalStatsView(aggregates);
    }

    private HistoricalStatsView buildHistoricalStatsView(List<ConnectionStatsAggregate> aggregates) {
        return HistoricalStatsView.builder()
                .dataTransferred(buildDataTransferredSeries(aggregates))
                .tokensCost(buildTokensCostSeries(aggregates))
                .tokensEarned(buildTokensEarnedSeries(aggregates))
                .connections(buildConnectionsSeries(aggregates))
                .performanceMetrics(buildPerformanceMetricsSeries(aggregates))
                .build();
    }

    private List<TimeSeriesPoint> buildDataTransferredSeries(List<ConnectionStatsAggregate> aggregates) {
        return aggregates.stream()
                .map(agg -> TimeSeriesPoint.builder()
                        .timestamp(agg.getAggregationDate())
                        .value(agg.getTotalDataTransferred())
                        .metric("dataTransferred")
                        .build())
                .collect(Collectors.toList());
    }

    private List<TimeSeriesPoint> buildTokensCostSeries(List<ConnectionStatsAggregate> aggregates) {
        return aggregates.stream()
                .map(agg -> TimeSeriesPoint.builder()
                        .timestamp(agg.getAggregationDate())
                        .value(agg.getTotalTokensCost())
                        .metric("tokensCost")
                        .build())
                .collect(Collectors.toList());
    }

    private List<TimeSeriesPoint> buildTokensEarnedSeries(List<ConnectionStatsAggregate> aggregates) {
        return aggregates.stream()
                .map(agg -> TimeSeriesPoint.builder()
                        .timestamp(agg.getAggregationDate())
                        .value(agg.getTotalTokensEarned())
                        .metric("tokensEarned")
                        .build())
                .collect(Collectors.toList());
    }

    private List<TimeSeriesPoint> buildConnectionsSeries(List<ConnectionStatsAggregate> aggregates) {
        return aggregates.stream()
                .map(agg -> TimeSeriesPoint.builder()
                        .timestamp(agg.getAggregationDate())
                        .value(new BigDecimal(agg.getTotalConnections()))
                        .metric("connections")
                        .build())
                .collect(Collectors.toList());
    }

    private List<TimeSeriesPoint> buildPerformanceMetricsSeries(List<ConnectionStatsAggregate> aggregates) {
        List<TimeSeriesPoint> metrics = new ArrayList<>();

        for (ConnectionStatsAggregate agg : aggregates) {
            metrics.add(TimeSeriesPoint.builder()
                    .timestamp(agg.getAggregationDate())
                    .value(new BigDecimal(agg.getAverageCpuUsage()))
                    .metric("cpuUsage")
                    .build());

            metrics.add(TimeSeriesPoint.builder()
                    .timestamp(agg.getAggregationDate())
                    .value(new BigDecimal(agg.getAverageMemoryUsage()))
                    .metric("memoryUsage")
                    .build());

            metrics.add(TimeSeriesPoint.builder()
                    .timestamp(agg.getAggregationDate())
                    .value(new BigDecimal(agg.getAverageNetworkSpeed()))
                    .metric("networkSpeed")
                    .build());
        }

        return metrics;
    }
}