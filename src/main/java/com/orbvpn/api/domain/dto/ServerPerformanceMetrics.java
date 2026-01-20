package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ServerPerformanceMetrics {
    private Long serverId;
    private String serverName;
    private Float averageCpuUsage;
    private Float averageMemoryUsage;
    private Float averageNetworkSpeed;
    private Integer activeConnections;
    private BigDecimal totalDataTransferred;
}
