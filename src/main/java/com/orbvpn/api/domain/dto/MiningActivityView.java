package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.MiningStatus;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MiningActivityView {
    private Long id;
    private MiningServerView server;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal dataTransferred;
    private BigDecimal connectionStability;
    private BigDecimal protocolEfficiency;
    private Boolean isActive;
    private BigDecimal currentReward;
    private MiningStatus status;
}