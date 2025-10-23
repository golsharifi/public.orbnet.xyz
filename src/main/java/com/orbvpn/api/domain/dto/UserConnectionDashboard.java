package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class UserConnectionDashboard {
    private ConnectionStatsView currentConnection;
    private List<ConnectionStatsView> recentConnections;
    private BigDecimal totalDataTransferred;
    private ConnectionAverages averageMetrics;
    private List<ServerUsageStats> serverHistory;
}