package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServerRealTimeMetrics {
    private Long serverId;
    private String serverName;
    private int activeConnections;
    private float cpuUsage;
    private float memoryUsage;
    private float networkUtilization;
    private MetricsTrend trend;
}
