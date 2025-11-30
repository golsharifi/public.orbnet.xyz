package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class HistoricalStatsView {
    private List<TimeSeriesPoint> dataTransferred;
    private List<TimeSeriesPoint> tokensCost;
    private List<TimeSeriesPoint> tokensEarned;
    private List<TimeSeriesPoint> connections;
    private List<TimeSeriesPoint> performanceMetrics;

    @Data
    @Builder
    public static class TimeSeriesPoint {
        private LocalDateTime timestamp;
        private BigDecimal value;
        private String metric;
    }
}