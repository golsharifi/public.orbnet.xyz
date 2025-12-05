package com.orbvpn.api.service.mining;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.ServerMetrics;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerMetricsService {
    private final ServerMetricsRepository serverMetricsRepository;
    private final MiningServerRepository miningServerRepository;

    @Scheduled(fixedRate = 60000) // Run every minute
    public void collectMetrics() {
        List<MiningServer> activeServers = miningServerRepository.findByMiningEnabledTrue();
        for (MiningServer server : activeServers) {
            try {
                ServerMetrics metrics = collectMetricsForServer(server);
                serverMetricsRepository.save(metrics);
                log.debug("Collected metrics for server {}", server.getId());
            } catch (Exception e) {
                log.error("Failed to collect metrics for server {}: {}",
                        server.getId(), e.getMessage());
            }
        }
    }

    private ServerMetrics collectMetricsForServer(MiningServer server) {
        BigDecimal uploadSpeed = measureUploadSpeed(server);
        BigDecimal downloadSpeed = measureDownloadSpeed(server);
        BigDecimal networkSpeed = uploadSpeed.add(downloadSpeed)
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        Integer latencyValue = measureLatency(server);

        return ServerMetrics.builder()
                .server(server)
                .cpuUsage(measureCpuUsage(server))
                .memoryUsage(measureMemoryUsage(server))
                .uploadSpeed(uploadSpeed)
                .downloadSpeed(downloadSpeed)
                .networkSpeed(networkSpeed)
                .activeConnections(countActiveConnections(server))
                .maxConnections(server.getMaxConnections())
                .latency(latencyValue)
                .packetLoss(measurePacketLoss(server))
                .connectionStability(calculateConnectionStability(server))
                .uptime(calculateUptime(server))
                .responseTime(measureResponseTime(server))
                .lastCheck(LocalDateTime.now())
                .dataTransferred(BigDecimal.ZERO) // Initialize with zero, update separately
                .build();
    }

    private BigDecimal measureCpuUsage(MiningServer server) {
        try {
            // Connect to server via SSH or monitoring API
            String command = "top -bn1 | grep 'Cpu(s)' | sed 's/.*, *\\([0-9.]*\\)%* id.*/\\1/' | awk '{print 100 - $1}'";
            String result = executeRemoteCommand(server, command);
            return new BigDecimal(result).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to measure CPU usage for server {}: {}", server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal measureMemoryUsage(MiningServer server) {
        try {
            String command = "free | grep Mem | awk '{print $3/$2 * 100.0}'";
            String result = executeRemoteCommand(server, command);
            return new BigDecimal(result).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to measure memory usage for server {}: {}", server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal measureUploadSpeed(MiningServer server) {
        try {
            // Implement speed test to a reliable endpoint
            String command = "speedtest-cli --simple | grep 'Upload' | awk '{print $2}'";
            String result = executeRemoteCommand(server, command);
            return new BigDecimal(result).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to measure upload speed for server {}: {}", server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal measureDownloadSpeed(MiningServer server) {
        try {
            String command = "speedtest-cli --simple | grep 'Download' | awk '{print $2}'";
            String result = executeRemoteCommand(server, command);
            return new BigDecimal(result).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to measure download speed for server {}: {}", server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private Integer measureLatency(MiningServer server) {
        try {
            String command = "ping -c 5 8.8.8.8 | tail -1 | awk '{print $4}' | cut -d '/' -f 2";
            String result = executeRemoteCommand(server, command);
            return Integer.parseInt(result);
        } catch (Exception e) {
            log.error("Failed to measure latency for server {}: {}", server.getId(), e.getMessage());
            return 0;
        }
    }

    private BigDecimal measurePacketLoss(MiningServer server) {
        try {
            String command = "ping -c 100 8.8.8.8 | grep -oP '\\d+(?=% packet loss)'";
            String result = executeRemoteCommand(server, command);
            return new BigDecimal(result).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to measure packet loss for server {}: {}", server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private Integer countActiveConnections(MiningServer server) {
        try {
            String command = "netstat -an | grep ESTABLISHED | wc -l";
            String result = executeRemoteCommand(server, command);
            return Integer.parseInt(result);
        } catch (Exception e) {
            log.error("Failed to count active connections for server {}: {}", server.getId(), e.getMessage());
            return 0;
        }
    }

    private BigDecimal calculateUptime(MiningServer server) {
        try {
            String command = "uptime -p | cut -d ' ' -f2-";
            String result = executeRemoteCommand(server, command);
            // Convert uptime string to decimal hours
            return parseUptimeToHours(result);
        } catch (Exception e) {
            log.error("Failed to calculate uptime for server {}: {}", server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private Integer measureResponseTime(MiningServer server) {
        try {
            long startTime = System.currentTimeMillis();
            executeRemoteCommand(server, "echo 1");
            long endTime = System.currentTimeMillis();
            return (int) (endTime - startTime);
        } catch (Exception e) {
            log.error("Failed to measure response time for server {}: {}", server.getId(), e.getMessage());
            return 0;
        }
    }

    private BigDecimal calculateConnectionStability(MiningServer server) {
        try {
            // Combine multiple factors for stability score
            BigDecimal packetLossScore = BigDecimal.ONE.subtract(
                    measurePacketLoss(server).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));

            BigDecimal latencyScore = new BigDecimal("1000")
                    .subtract(new BigDecimal(measureLatency(server)))
                    .divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO);

            BigDecimal uptimeScore = calculateUptime(server)
                    .divide(new BigDecimal("24"), 4, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE);

            return packetLossScore
                    .multiply(new BigDecimal("0.4"))
                    .add(latencyScore.multiply(new BigDecimal("0.3")))
                    .add(uptimeScore.multiply(new BigDecimal("0.3")));
        } catch (Exception e) {
            log.error("Failed to calculate connection stability for server {}: {}", server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String executeRemoteCommand(MiningServer server, String command) {
        // Implement secure remote command execution
        // This could be via SSH, REST API, or other secure protocols
        // Return the command output as a string
        return ""; // Placeholder
    }

    private BigDecimal parseUptimeToHours(String uptimeString) {
        // Convert uptime string to decimal hours
        // Example: "10 days, 5 hours, 30 minutes" -> 250.5
        return BigDecimal.ZERO; // Placeholder
    }

    public ServerMiningMetrics getServerMetrics(Long serverId) {
        MiningServer server = miningServerRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        ServerMetrics metrics = serverMetricsRepository
                .findFirstByServerOrderByLastCheckDesc(server);

        return ServerMiningMetrics.builder()
                .server(convertToServerView(server))
                .cpuUsage(metrics.getCpuUsage())
                .memoryUsage(metrics.getMemoryUsage())
                .uploadSpeed(metrics.getUploadSpeed())
                .downloadSpeed(metrics.getDownloadSpeed())
                .networkSpeed(metrics.getNetworkSpeed())
                .activeConnections(metrics.getActiveConnections())
                .maxConnections(metrics.getMaxConnections())
                .uptime(metrics.getUptime())
                .responseTime(metrics.getResponseTime())
                .lastHeartbeat(metrics.getLastCheck())
                .miningRate(calculateMiningRate(server))
                .totalMiners(countTotalMiners(server))
                .build();
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

    // Add missing methods for mining rate and miner count
    private BigDecimal calculateMiningRate(MiningServer server) {
        try {
            ServerMetrics metrics = serverMetricsRepository
                    .findFirstByServerOrderByLastCheckDesc(server);
            if (metrics == null) {
                return BigDecimal.ZERO;
            }

            // Calculate mining rate based on server performance and active miners
            BigDecimal baseRate = new BigDecimal("0.1"); // Base rate per hour
            BigDecimal performanceMultiplier = calculatePerformanceMultiplier(metrics);
            int minerCount = countTotalMiners(server);

            return baseRate
                    .multiply(performanceMultiplier)
                    .multiply(BigDecimal.valueOf(minerCount))
                    .setScale(8, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to calculate mining rate for server {}: {}",
                    server.getId(), e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private int countTotalMiners(MiningServer server) {
        try {
            String command = "netstat -an | grep ESTABLISHED | grep ':mining' | wc -l";
            String result = executeRemoteCommand(server, command);
            return Integer.parseInt(result.trim());
        } catch (Exception e) {
            log.error("Failed to count total miners for server {}: {}",
                    server.getId(), e.getMessage());
            return 0;
        }
    }

    private BigDecimal calculatePerformanceMultiplier(ServerMetrics metrics) {
        BigDecimal cpuScore = BigDecimal.ONE.subtract(
                metrics.getCpuUsage().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));

        BigDecimal memoryScore = BigDecimal.ONE.subtract(
                metrics.getMemoryUsage().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));

        BigDecimal networkScore = metrics.getNetworkSpeed()
                .divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);

        return cpuScore
                .add(memoryScore)
                .add(networkScore)
                .divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
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
                .networkSpeed(metrics.getNetworkSpeed())
                .activeConnections(metrics.getActiveConnections())
                .maxConnections(metrics.getMaxConnections())
                .uptime(metrics.getUptime())
                .responseTime(metrics.getResponseTime())
                .lastCheck(metrics.getLastCheck())
                .build();
    }
}
