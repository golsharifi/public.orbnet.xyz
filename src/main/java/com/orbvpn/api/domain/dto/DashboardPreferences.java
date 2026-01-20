package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.AlertType;
import com.orbvpn.api.domain.enums.AlertSeverity;
import lombok.Builder;
import lombok.Data;
import java.util.Set;

@Data
@Builder
public class DashboardPreferences {
    private String sessionId;
    private RefreshInterval refreshInterval;
    private Set<String> visibleMetrics;
    private AlertSettings alertSettings;
    private ChartSettings chartSettings;

    @Data
    @Builder
    public static class AlertSettings {
        private boolean enableAlerts;
        private Set<AlertType> alertTypes;
        private AlertSeverity minimumSeverity;
        private boolean soundEnabled;
    }

    @Data
    @Builder
    public static class ChartSettings {
        private int dataPointsLimit;
        private String defaultTimeRange;
        private boolean showLegend;
        private boolean showGridLines;
    }

    public enum RefreshInterval {
        REALTIME(0),
        SECONDS_5(5),
        SECONDS_10(10),
        SECONDS_30(30),
        MINUTES_1(60);

        private final int seconds;

        RefreshInterval(int seconds) {
            this.seconds = seconds;
        }

        public int getSeconds() {
            return seconds;
        }
    }
}
