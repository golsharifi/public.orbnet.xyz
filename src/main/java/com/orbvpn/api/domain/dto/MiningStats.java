package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MiningStats {
    private BigDecimal totalRewards;
    private BigDecimal todayRewards;
    private Integer activeMiningSessions;
    private BigDecimal averageDailyReward;
    private List<MiningServerView> topServers;
    private List<MiningRewardView> rewardHistory;

}
