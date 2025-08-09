package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;

@Data
@Builder
public class StakingStats {
    private BigDecimal totalStaked;
    private BigDecimal totalRewardsEarned;
    private Integer activeStakes;
    private BigDecimal averageApy;
}