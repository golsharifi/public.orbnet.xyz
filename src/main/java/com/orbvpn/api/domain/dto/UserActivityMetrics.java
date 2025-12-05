package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class UserActivityMetrics {
    private Integer userId;
    private String username;
    private Integer totalConnections;
    private Integer activeConnections;
    private BigDecimal totalDataTransferred;
    private Float averageSessionDuration;
}
