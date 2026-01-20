package com.orbvpn.api.service;

import com.orbvpn.api.domain.enums.AlertSeverity;
import com.orbvpn.api.domain.dto.DashboardPreferences;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DashboardPreferencesService {
    private final Map<String, DashboardPreferences> sessionPreferences = new ConcurrentHashMap<>();

    public void updatePreferences(String sessionId, DashboardPreferences preferences) {
        sessionPreferences.put(sessionId, preferences);
        log.debug("Updated preferences for session {}: {}", sessionId, preferences);
    }

    public DashboardPreferences getPreferences(String sessionId) {
        return sessionPreferences.getOrDefault(sessionId, createDefaultPreferences(sessionId));
    }

    public void removePreferences(String sessionId) {
        sessionPreferences.remove(sessionId);
        log.debug("Removed preferences for session {}", sessionId);
    }

    private DashboardPreferences createDefaultPreferences(String sessionId) {
        return DashboardPreferences.builder()
                .sessionId(sessionId)
                .refreshInterval(DashboardPreferences.RefreshInterval.SECONDS_10)
                .alertSettings(DashboardPreferences.AlertSettings.builder()
                        .enableAlerts(true)
                        .minimumSeverity(AlertSeverity.WARNING)
                        .soundEnabled(false)
                        .build())
                .chartSettings(DashboardPreferences.ChartSettings.builder()
                        .dataPointsLimit(100)
                        .defaultTimeRange("1h")
                        .showLegend(true)
                        .showGridLines(true)
                        .build())
                .build();
    }
}