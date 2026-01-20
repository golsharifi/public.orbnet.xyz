package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ServerUsageStats {
    private Long serverId;
    private String serverName;
    private Integer totalConnections;
    private Integer totalTime;
    private BigDecimal totalDataTransferred;
    private ConnectionAverages averagePerformance;
    private LocalDateTime lastUsed;
}