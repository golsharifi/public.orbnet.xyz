package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ServerMetrics {
    private BigDecimal cpuUsage;
    private BigDecimal memoryUsage;
    private BigDecimal uploadSpeed;
    private BigDecimal downloadSpeed;
    private BigDecimal networkSpeed;
    private Integer activeConnections;
    private Integer maxConnections;
    private BigDecimal uptime;
    private Integer responseTime;
    private LocalDateTime lastCheck;
}