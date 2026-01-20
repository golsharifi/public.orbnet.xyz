package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ServerStatus {
    private Long serverId;
    private String serverName;
    private String location;
    private int activeConnections;
    private float cpuUsage;
    private float memoryUsage;
    private float networkUtilization;
    private BigDecimal tokenEarnings;
}