package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class NetworkOverview {
    private int totalActiveConnections;
    private int totalServers;
    private int activeServers;
    private BigDecimal totalDataTransferred;
    private float averageNetworkUtilization;
    private Map<String, Integer> connectionsByRegion;
}