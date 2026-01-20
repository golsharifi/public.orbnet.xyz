package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class NetworkAnalytics {
    private int totalActiveConnections;
    private int totalUsers;
    private int totalServers;
    private BigDecimal totalDataTransferred;
    private Map<String, Integer> connectionsPerServer;
    private Map<String, BigDecimal> dataPerServer;
    private List<ServerPerformanceMetrics> topPerformingServers;
    private List<UserActivityMetrics> mostActiveUsers;
}
