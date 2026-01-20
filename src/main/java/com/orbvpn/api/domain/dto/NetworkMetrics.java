package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class NetworkMetrics {
    private float totalBandwidth;
    private float averageLatency;
    private int totalActiveUsers;
    private Map<String, Integer> connectionsByRegion;
    private BigDecimal currentTokenRate;
}
