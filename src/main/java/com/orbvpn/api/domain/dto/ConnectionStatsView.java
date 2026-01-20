package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ConnectionStatsView {
    private Long id;
    private Long serverId;
    private String serverName;
    private LocalDateTime connectionStart;
    private LocalDateTime connectionEnd;
    private BigDecimal dataTransferred;
    private Float cpuUsage;
    private Float memoryUsage;
    private Float uploadSpeed;
    private Float downloadSpeed;
    private Float networkSpeed;
    private Integer responseTime;
    private Integer latency;
    private BigDecimal tokensCost;
    private BigDecimal tokensEarned;
}