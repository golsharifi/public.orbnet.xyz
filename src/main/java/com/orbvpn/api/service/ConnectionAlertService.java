package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.AlertMessage;
import com.orbvpn.api.domain.enums.AlertSeverity;
import com.orbvpn.api.domain.enums.AlertType;
import com.orbvpn.api.domain.entity.ServerMetrics;
import com.orbvpn.api.domain.entity.MiningServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.function.Consumer;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionAlertService {
    private final SimpMessagingTemplate messagingTemplate;
    private final AsyncNotificationHelper asyncNotificationHelper;

    // Thresholds
    private static final float CPU_THRESHOLD = 90.0f;
    private static final float MEMORY_THRESHOLD = 85.0f;
    private static final float NETWORK_UTILIZATION_THRESHOLD = 95.0f;
    private static final int HIGH_LATENCY_THRESHOLD = 500; // ms

    // Alert cooldown tracking
    private final Map<String, LocalDateTime> lastAlerts = new ConcurrentHashMap<>();
    private static final long ALERT_COOLDOWN_MINUTES = 15;

    public void checkServerMetrics(ServerMetrics metrics, MiningServer server) {
        if (metrics.getCpuUsage().floatValue() > CPU_THRESHOLD) {
            createAlert(AlertType.HIGH_CPU, server, metrics);
        }

        if (metrics.getMemoryUsage().floatValue() > MEMORY_THRESHOLD) {
            createAlert(AlertType.HIGH_MEMORY, server, metrics);
        }

        if (metrics.getNetworkSpeed().floatValue() > NETWORK_UTILIZATION_THRESHOLD) {
            createAlert(AlertType.HIGH_NETWORK_UTILIZATION, server, metrics);
        }

        if (metrics.getLatency() > HIGH_LATENCY_THRESHOLD) {
            createAlert(AlertType.HIGH_LATENCY, server, metrics);
        }
    }

    private void createAlert(AlertType type, MiningServer server, ServerMetrics metrics) {
        String alertKey = String.format("%s_%s", server.getId(), type);

        // Check cooldown
        if (isAlertInCooldown(alertKey)) {
            return;
        }

        AlertMessage alert = buildAlertMessage(type, server, metrics);
        broadcastAlert(alert);
        notifyAdmins(alert);

        // Update cooldown
        lastAlerts.put(alertKey, LocalDateTime.now());
    }

    private boolean isAlertInCooldown(String alertKey) {
        LocalDateTime lastAlert = lastAlerts.get(alertKey);
        if (lastAlert == null) {
            return false;
        }

        return LocalDateTime.now().minusMinutes(ALERT_COOLDOWN_MINUTES).isBefore(lastAlert);
    }

    private AlertMessage buildAlertMessage(AlertType type, MiningServer server, ServerMetrics metrics) {
        String message = generateAlertMessage(type, server, metrics);
        AlertSeverity severity = determineAlertSeverity(type, metrics);

        return AlertMessage.builder()
                .type(type)
                .serverId(server.getId().toString())
                .serverName(server.getHostName())
                .message(message)
                .severity(severity)
                .timestamp(LocalDateTime.now())
                .metadata(buildAlertMetadata(type, metrics))
                .build();
    }

    private String generateAlertMessage(AlertType type, MiningServer server, ServerMetrics metrics) {
        return switch (type) {
            case HIGH_CPU -> String.format("High CPU usage (%s%%) detected on server %s",
                    metrics.getCpuUsage().setScale(2), server.getHostName());
            case HIGH_MEMORY -> String.format("High memory usage (%s%%) detected on server %s",
                    metrics.getMemoryUsage().setScale(2), server.getHostName());
            case HIGH_NETWORK_UTILIZATION -> String.format("High network utilization (%s%%) detected on server %s",
                    metrics.getNetworkSpeed().setScale(2), server.getHostName());
            case HIGH_LATENCY -> String.format("High latency (%dms) detected on server %s",
                    metrics.getLatency(), server.getHostName());
            default -> "Alert detected on server " + server.getHostName();
        };
    }

    private AlertSeverity determineAlertSeverity(AlertType type, ServerMetrics metrics) {
        return switch (type) {
            case HIGH_CPU -> metrics.getCpuUsage().floatValue() > 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            case HIGH_MEMORY ->
                metrics.getMemoryUsage().floatValue() > 90 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            case HIGH_NETWORK_UTILIZATION ->
                metrics.getNetworkSpeed().floatValue() > 98 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            case HIGH_LATENCY -> metrics.getLatency() > 1000 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            default -> AlertSeverity.INFO;
        };
    }

    private Map<String, Object> buildAlertMetadata(AlertType type, ServerMetrics metrics) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", LocalDateTime.now());

        switch (type) {
            case HIGH_CPU:
                metadata.put("cpuUsage", metrics.getCpuUsage());
                metadata.put("threshold", CPU_THRESHOLD);
                break;
            case HIGH_MEMORY:
                metadata.put("memoryUsage", metrics.getMemoryUsage());
                metadata.put("threshold", MEMORY_THRESHOLD);
                break;
            case HIGH_NETWORK_UTILIZATION:
                metadata.put("networkUtilization", metrics.getNetworkSpeed());
                metadata.put("threshold", NETWORK_UTILIZATION_THRESHOLD);
                break;
            case HIGH_LATENCY:
                metadata.put("latency", metrics.getLatency());
                metadata.put("threshold", HIGH_LATENCY_THRESHOLD);
                break;
            case CONNECTION_SPIKE:
                metadata.put("activeConnections", metrics.getActiveConnections());
                metadata.put("maxConnections", metrics.getMaxConnections());
                break;
            case TOKEN_RATE_CHANGE:
                // Add relevant token rate metadata
                metadata.put("currentRate", "N/A"); // Add actual token rate data
                metadata.put("previousRate", "N/A"); // Add previous rate data
                break;
            case SECURITY_ALERT:
                // Add security-related metadata
                metadata.put("alertSource", "system");
                metadata.put("severity", "high");
                break;
        }

        return metadata;
    }

    private void notifyAdmins(AlertMessage alert) {
        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            asyncNotificationHelper.sendAdminNotificationAsync(
                    alert.getMessage(),
                    "Critical Alert",
                    buildNotificationMetadata(alert));
        }
    }

    private Map<String, String> buildNotificationMetadata(AlertMessage alert) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("alertType", alert.getType().toString());
        metadata.put("severity", alert.getSeverity().toString());
        metadata.put("serverId", alert.getServerId());
        metadata.put("serverName", alert.getServerName());
        return metadata;
    }

    private final Map<String, Consumer<AlertMessage>> subscribers = new ConcurrentHashMap<>();

    public void subscribeToAlerts(String sessionId, Consumer<AlertMessage> alertConsumer) {
        subscribers.put(sessionId, alertConsumer);
        log.debug("Session {} subscribed to alerts", sessionId);
    }

    public void unsubscribeFromAlerts(String sessionId) {
        subscribers.remove(sessionId);
        log.debug("Session {} unsubscribed from alerts", sessionId);
    }

    // Modify your existing broadcastAlert method to also notify subscribers
    private void broadcastAlert(AlertMessage alert) {
        messagingTemplate.convertAndSend("/topic/alerts", alert);

        // Notify websocket subscribers
        subscribers.forEach((sessionId, consumer) -> {
            try {
                consumer.accept(alert);
            } catch (Exception e) {
                log.error("Error sending alert to session {}: {}", sessionId, e.getMessage());
            }
        });
    }
}