package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class RealTimeMetrics {
    private LocalDateTime timestamp;
    private int activeConnections;
    private Map<String, ServerRealTimeMetrics> serverMetrics;
    private NetworkMetrics networkMetrics;
}
