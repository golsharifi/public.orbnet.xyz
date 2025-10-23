package com.orbvpn.api.domain.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AggregatedMetrics {
    private BigDecimal averageCpuUsage;
    private BigDecimal averageMemoryUsage;
    private BigDecimal averageNetworkSpeed;
    private double averageActiveConnections;
    private BigDecimal totalUptime;
    private double averageResponseTime;
}
