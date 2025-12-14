package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.repository.ConnectionStatsRepository;
import com.orbvpn.api.repository.ConnectionStatsAggregateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionStatsExportService {
    private final ConnectionStatsRepository connectionStatsRepository;
    private final ConnectionStatsAggregateRepository aggregateRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] DETAILED_HEADERS = {
            "Date", "User", "Server", "Data Transferred (GB)", "CPU Usage (%)",
            "Memory Usage (%)", "Network Speed (Mbps)", "Response Time (ms)",
            "Tokens Cost", "Tokens Earned", "Duration (minutes)"
    };

    private static final String[] AGGREGATE_HEADERS = {
            "Period", "User", "Server", "Total Data (GB)", "Total Connections",
            "Avg CPU Usage (%)", "Avg Memory Usage (%)", "Avg Network Speed (Mbps)",
            "Total Tokens Cost", "Total Tokens Earned"
    };

    @Transactional(readOnly = true)
    public byte[] exportDetailedStats(LocalDateTime from, LocalDateTime to, Integer userId) {
        List<ConnectionStats> stats;
        if (userId != null) {
            stats = connectionStatsRepository.findByUserIdAndPeriod(userId, from, to);
        } else {
            stats = connectionStatsRepository.findByPeriod(from, to);
        }

        return generateDetailedCsv(stats);
    }

    @Transactional(readOnly = true)
    public byte[] exportAggregateStats(
            LocalDateTime from,
            LocalDateTime to,
            ConnectionStatsAggregate.AggregationPeriod period,
            Integer userId) {

        List<ConnectionStatsAggregate> aggregates;
        if (userId != null) {
            aggregates = aggregateRepository.findByUserAndPeriodAndAggregationDateBetween(
                    userRepository.findById(userId).orElseThrow(),
                    period,
                    from,
                    to);
        } else {
            aggregates = aggregateRepository.findByPeriodAndAggregationDateBetween(period, from, to);
        }

        return generateAggregateCsv(aggregates);
    }

    private byte[] generateDetailedCsv(List<ConnectionStats> stats) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader(DETAILED_HEADERS).build())) {

            for (ConnectionStats stat : stats) {
                csvPrinter.printRecord(
                        DATE_FORMATTER.format(stat.getConnectionStart()),
                        stat.getUser().getEmail(),
                        stat.getServer().getHostName(),
                        stat.getDataTransferred(),
                        stat.getCpuUsage(),
                        stat.getMemoryUsage(),
                        stat.getNetworkSpeed(),
                        stat.getResponseTime(),
                        stat.getTokensCost(),
                        stat.getTokensEarned(),
                        calculateDurationInMinutes(stat));
            }

            csvPrinter.flush();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generating detailed CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }

    private byte[] generateAggregateCsv(List<ConnectionStatsAggregate> aggregates) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.Builder.create(CSVFormat.DEFAULT).setHeader(AGGREGATE_HEADERS).build())) {

            for (ConnectionStatsAggregate agg : aggregates) {
                csvPrinter.printRecord(
                        DATE_FORMATTER.format(agg.getAggregationDate()),
                        agg.getUser().getEmail(),
                        agg.getServer().getHostName(),
                        agg.getTotalDataTransferred(),
                        agg.getTotalConnections(),
                        agg.getAverageCpuUsage(),
                        agg.getAverageMemoryUsage(),
                        agg.getAverageNetworkSpeed(),
                        agg.getTotalTokensCost(),
                        agg.getTotalTokensEarned());
            }

            csvPrinter.flush();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Error generating aggregate CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }

    private long calculateDurationInMinutes(ConnectionStats stat) {
        LocalDateTime end = stat.getConnectionEnd() != null ? stat.getConnectionEnd() : LocalDateTime.now();
        return java.time.Duration.between(stat.getConnectionStart(), end).toMinutes();
    }
}