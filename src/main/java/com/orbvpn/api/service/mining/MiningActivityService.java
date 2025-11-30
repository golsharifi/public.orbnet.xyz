package com.orbvpn.api.service.mining;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.ServerMetrics;
import com.orbvpn.api.domain.enums.ConnectionStatus;
import com.orbvpn.api.domain.enums.MiningStatus;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.time.Duration;
import java.math.RoundingMode;
import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MiningActivityService {
    private final MiningActivityRepository miningActivityRepository;
    private final ServerMetricsRepository serverMetricsRepository;
    private final ConnectionLogRepository connectionLogRepository;
    private final MiningServerRepository miningServerRepository;

    private void updateFinalMetrics(MiningActivity activity) {
        LocalDateTime startTime = activity.getStartTime();
        LocalDateTime endTime = LocalDateTime.now();

        // Calculate final data transfer
        BigDecimal totalDataTransferred = calculateDataTransferred(activity, startTime, endTime);
        activity.setDataTransferred(totalDataTransferred);

        // Calculate connection stability
        BigDecimal connectionStability = calculateConnectionStability(activity, startTime, endTime);
        activity.setConnectionStability(connectionStability);

        // Calculate protocol efficiency
        BigDecimal protocolEfficiency = calculateProtocolEfficiency(activity, startTime, endTime);
        activity.setProtocolEfficiency(protocolEfficiency);

        // Update server metrics
        updateServerMetrics(activity.getServer(), totalDataTransferred);
    }

    private BigDecimal calculateDataTransferred(MiningActivity activity,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        List<ConnectionLog> logs = connectionLogRepository
                .findByUserAndServerAndTimestampBetween(
                        activity.getUser(),
                        activity.getServer(),
                        startTime,
                        endTime);

        return logs.stream()
                .map(ConnectionLog::getDataTransferred)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateConnectionStability(MiningActivity activity,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        // Get connection logs for the period
        List<ConnectionLog> logs = connectionLogRepository
                .findByUserAndServerAndTimestampBetween(
                        activity.getUser(),
                        activity.getServer(),
                        startTime,
                        endTime);

        if (logs.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate stability based on connection drops and latency
        long totalMinutes = Duration.between(startTime, endTime).toMinutes();
        long connectedMinutes = logs.stream()
                .filter(log -> log.getStatus() == ConnectionStatus.CONNECTED)
                .count();

        BigDecimal uptime = BigDecimal.valueOf(connectedMinutes)
                .divide(BigDecimal.valueOf(totalMinutes), 4, RoundingMode.HALF_UP);

        BigDecimal avgLatency = calculateAverageLatency(logs);
        BigDecimal latencyScore = calculateLatencyScore(avgLatency);

        return uptime.multiply(latencyScore)
                .multiply(new BigDecimal("0.8"))
                .add(new BigDecimal("0.2")); // Base stability score
    }

    private BigDecimal calculateProtocolEfficiency(MiningActivity activity,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        List<ConnectionLog> logs = connectionLogRepository
                .findByUserAndServerAndTimestampBetween(
                        activity.getUser(),
                        activity.getServer(),
                        startTime,
                        endTime);

        if (logs.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate efficiency based on protocol performance metrics
        BigDecimal avgThroughput = calculateAverageThroughput(logs);
        BigDecimal packetLossRate = calculatePacketLossRate(logs);
        BigDecimal protocolOverhead = calculateProtocolOverhead(logs);

        return calculateEfficiencyScore(avgThroughput, packetLossRate, protocolOverhead);
    }

    private BigDecimal calculateAverageLatency(List<ConnectionLog> logs) {
        return logs.stream()
                .map(ConnectionLog::getLatency)
                .filter(Objects::nonNull)
                .map(latency -> BigDecimal.valueOf(latency.longValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLatencyScore(BigDecimal avgLatency) {
        // Convert latency to score (lower latency = higher score)
        BigDecimal maxAcceptableLatency = new BigDecimal("200"); // 200ms
        return BigDecimal.ONE.subtract(
                avgLatency.min(maxAcceptableLatency)
                        .divide(maxAcceptableLatency, 4, RoundingMode.HALF_UP));
    }

    private BigDecimal calculateAverageThroughput(List<ConnectionLog> logs) {
        return logs.stream()
                .map(ConnectionLog::getThroughput)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePacketLossRate(List<ConnectionLog> logs) {
        return logs.stream()
                .map(ConnectionLog::getPacketLoss)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logs.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateProtocolOverhead(List<ConnectionLog> logs) {
        return logs.stream()
                .map(ConnectionLog::getProtocolOverhead)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logs.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateEfficiencyScore(BigDecimal throughput,
            BigDecimal packetLoss,
            BigDecimal overhead) {
        // Normalize and weight each component
        BigDecimal throughputScore = normalizeThroughput(throughput).multiply(new BigDecimal("0.4"));
        BigDecimal packetLossScore = (BigDecimal.ONE.subtract(packetLoss)).multiply(new BigDecimal("0.4"));
        BigDecimal overheadScore = (BigDecimal.ONE.subtract(overhead)).multiply(new BigDecimal("0.2"));

        return throughputScore.add(packetLossScore).add(overheadScore);
    }

    private BigDecimal normalizeThroughput(BigDecimal throughput) {
        BigDecimal maxThroughput = new BigDecimal("1000"); // 1 Gbps
        return throughput.divide(maxThroughput, 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
    }

    private void updateServerMetrics(MiningServer server, BigDecimal dataTransferred) {
        ServerMetrics metrics = serverMetricsRepository.findFirstByServerOrderByLastCheckDesc(server);
        if (metrics != null) {
            metrics.setDataTransferred(metrics.getDataTransferred().add(dataTransferred));
            serverMetricsRepository.save(metrics);
        }
    }

    private MiningServerView convertToServerView(MiningServer server) {
        ServerMetrics metrics = serverMetricsRepository
                .findFirstByServerOrderByLastCheckDesc(server);

        return MiningServerView.builder()
                .id(server.getId())
                .hostName(server.getHostName())
                .publicIp(server.getPublicIp())
                .location(server.getLocation())
                .country(server.getCountry())
                .continent(server.getContinent())
                .isMiningEnabled(server.getMiningEnabled())
                .activeConnections(metrics != null ? metrics.getActiveConnections() : 0)
                .metrics(convertToMetricsView(metrics))
                .build();
    }

    private com.orbvpn.api.domain.dto.ServerMetrics convertToMetricsView(ServerMetrics metrics) {
        if (metrics == null) {
            return null;
        }

        return com.orbvpn.api.domain.dto.ServerMetrics.builder()
                .cpuUsage(metrics.getCpuUsage())
                .memoryUsage(metrics.getMemoryUsage())
                .uploadSpeed(metrics.getUploadSpeed())
                .downloadSpeed(metrics.getDownloadSpeed())
                .networkSpeed(calculateNetworkSpeed(metrics))
                .activeConnections(metrics.getActiveConnections())
                .maxConnections(metrics.getMaxConnections())
                .uptime(metrics.getUptime())
                .responseTime(metrics.getResponseTime())
                .lastCheck(metrics.getLastCheck())
                .build();
    }

    private BigDecimal calculateNetworkSpeed(ServerMetrics metrics) {
        return metrics.getUploadSpeed()
                .add(metrics.getDownloadSpeed())
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    public MiningActivityView getCurrentActivity(User user) {
        MiningActivity activity = miningActivityRepository
                .findFirstByUserAndIsActiveTrue(user)
                .orElse(null);

        if (activity == null) {
            return null;
        }

        return convertToView(activity);
    }

    @Transactional
    public MiningActivityView startMining(User user, Long serverId) {
        MiningServer server = miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        MiningActivity activity = MiningActivity.builder()
                .user(user)
                .server(server)
                .startTime(LocalDateTime.now())
                .isActive(true)
                .status(MiningStatus.ACTIVE)
                .build();

        activity = miningActivityRepository.save(activity);
        return convertToView(activity);
    }

    @Transactional
    public MiningActivityView stopMining(User user, Long serverId) {
        MiningServer miningServer = miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        MiningActivity activity = miningActivityRepository
                .findByUserAndServerAndIsActiveTrue(user, miningServer)
                .orElseThrow(() -> new NotFoundException("No active mining session found"));

        updateFinalMetrics(activity);
        activity.setEndTime(LocalDateTime.now());
        activity.setIsActive(false);
        activity.setStatus(MiningStatus.COMPLETED);

        activity = miningActivityRepository.save(activity);
        return convertToView(activity);
    }

    private MiningActivityView convertToView(MiningActivity activity) {
        return MiningActivityView.builder()
                .id(activity.getId())
                .server(convertToServerView(activity.getServer()))
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .dataTransferred(activity.getDataTransferred())
                .connectionStability(activity.getConnectionStability())
                .protocolEfficiency(activity.getProtocolEfficiency())
                .isActive(activity.getIsActive())
                .currentReward(activity.getRewardEarned())
                .status(activity.getStatus())
                .build();
    }
}
