package com.orbvpn.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.service.ConnectionAdminDashboardService;
import com.orbvpn.api.domain.dto.AdminDashboardView;
import com.orbvpn.api.service.ConnectionAlertService;
import com.orbvpn.api.domain.enums.AlertSeverity;
import com.orbvpn.api.domain.enums.AlertType;

import com.orbvpn.api.domain.dto.DashboardPreferences;
import com.orbvpn.api.service.DashboardPreferencesService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardWebSocketHandler implements WebSocketHandler {
    private final ObjectMapper objectMapper;
    private final ConnectionAdminDashboardService dashboardService;
    private final ConnectionAlertService alertService;
    private final DashboardPreferencesService preferencesService;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        sendInitialData(session);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            String payload = (String) message.getPayload();
            DashboardCommand command = objectMapper.readValue(payload, DashboardCommand.class);
            handleCommand(session, command);
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            sendError(session, "Error processing command");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        sessions.remove(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void sendInitialData(WebSocketSession session) {
        try {
            AdminDashboardView dashboardData = dashboardService.getDashboardData();
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "INITIAL_DATA",
                    "data", dashboardData));
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.error("Error sending initial data: {}", e.getMessage(), e);
        }
    }

    private void handleCommand(WebSocketSession session, DashboardCommand command) {
        try {
            switch (command.getType()) {
                case "REFRESH_DATA":
                    sendDashboardData(session);
                    break;
                case "SUBSCRIBE_ALERTS":
                    handleAlertSubscription(session, command);
                    break;
                case "UPDATE_PREFERENCES":
                    handlePreferencesUpdate(session, command);
                    break;
                default:
                    sendError(session, "Unknown command type: " + command.getType());
            }
        } catch (Exception e) {
            log.error("Error handling command: {}", e.getMessage(), e);
            sendError(session, "Error processing command");
        }
    }

    private void sendDashboardData(WebSocketSession session) throws Exception {
        AdminDashboardView dashboardData = dashboardService.getDashboardData();
        String message = objectMapper.writeValueAsString(Map.of(
                "type", "DASHBOARD_UPDATE",
                "data", dashboardData));
        session.sendMessage(new TextMessage(message));
    }

    private void handleAlertSubscription(WebSocketSession session, DashboardCommand command) {
        try {
            boolean subscribeToAlerts = (boolean) command.getData().getOrDefault("subscribe", false);
            if (subscribeToAlerts) {
                alertService.subscribeToAlerts(session.getId(), alert -> {
                    try {
                        String message = objectMapper.writeValueAsString(Map.of(
                                "type", "ALERT",
                                "data", alert));
                        session.sendMessage(new TextMessage(message));
                    } catch (Exception e) {
                        log.error("Error sending alert: {}", e.getMessage(), e);
                    }
                });
            } else {
                alertService.unsubscribeFromAlerts(session.getId());
            }
        } catch (Exception e) {
            log.error("Error handling alert subscription: {}", e.getMessage(), e);
            sendError(session, "Error processing alert subscription");
        }
    }

    private void handlePreferencesUpdate(WebSocketSession session, DashboardCommand command) {
        try {
            Map<String, Object> preferencesData = (Map<String, Object>) command.getData();

            DashboardPreferences preferences = convertToPreferences(session.getId(), preferencesData);
            preferencesService.updatePreferences(session.getId(), preferences);

            // Send confirmation
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "PREFERENCES_UPDATED",
                    "data", preferences));
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.error("Error updating preferences: {}", e.getMessage(), e);
            sendError(session, "Error updating preferences");
        }
    }

    private DashboardPreferences convertToPreferences(String sessionId, Map<String, Object> data) {
        return DashboardPreferences.builder()
                .sessionId(sessionId)
                .refreshInterval(parseRefreshInterval(data.get("refreshInterval")))
                .visibleMetrics(parseVisibleMetrics(data.get("visibleMetrics")))
                .alertSettings(parseAlertSettings(data.get("alertSettings")))
                .chartSettings(parseChartSettings(data.get("chartSettings")))
                .build();
    }

    // Add helper methods for parsing preference data
    private DashboardPreferences.RefreshInterval parseRefreshInterval(Object value) {
        if (value == null)
            return DashboardPreferences.RefreshInterval.SECONDS_10;
        return DashboardPreferences.RefreshInterval.valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseVisibleMetrics(Object value) {
        if (value == null)
            return new HashSet<>();
        return new HashSet<>((List<String>) value);
    }

    @SuppressWarnings("unchecked")
    private DashboardPreferences.AlertSettings parseAlertSettings(Object value) {
        if (value == null)
            return DashboardPreferences.AlertSettings.builder().build();
        Map<String, Object> settings = (Map<String, Object>) value;
        return DashboardPreferences.AlertSettings.builder()
                .enableAlerts((Boolean) settings.getOrDefault("enableAlerts", true))
                .alertTypes(parseAlertTypes(settings.get("alertTypes")))
                .minimumSeverity(parseAlertSeverity(settings.get("minimumSeverity")))
                .soundEnabled((Boolean) settings.getOrDefault("soundEnabled", false))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DashboardPreferences.ChartSettings parseChartSettings(Object value) {
        if (value == null)
            return DashboardPreferences.ChartSettings.builder().build();
        Map<String, Object> settings = (Map<String, Object>) value;
        return DashboardPreferences.ChartSettings.builder()
                .dataPointsLimit((Integer) settings.getOrDefault("dataPointsLimit", 100))
                .defaultTimeRange((String) settings.getOrDefault("defaultTimeRange", "1h"))
                .showLegend((Boolean) settings.getOrDefault("showLegend", true))
                .showGridLines((Boolean) settings.getOrDefault("showGridLines", true))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Set<AlertType> parseAlertTypes(Object value) {
        if (value == null)
            return EnumSet.allOf(AlertType.class);
        return ((List<String>) value).stream()
                .map(AlertType::valueOf)
                .collect(Collectors.toSet());
    }

    private AlertSeverity parseAlertSeverity(Object value) {
        if (value == null)
            return AlertSeverity.WARNING;
        return AlertSeverity.valueOf(value.toString());
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "ERROR",
                    "message", errorMessage));
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.error("Error sending error message: {}", e.getMessage(), e);
        }
    }

    @Data
    public static class DashboardCommand {
        private String type;
        private Map<String, Object> data;
    }
}