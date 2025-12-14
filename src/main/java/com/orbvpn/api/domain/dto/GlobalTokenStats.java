package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class GlobalTokenStats {
    private long totalActiveUsers;
    private long totalDailyActiveUsers;
    private BigDecimal totalTokensInCirculation;
    private BigDecimal totalTokensEarnedToday;
    private BigDecimal totalTokensSpentToday;
    private int totalAdsWatchedToday;
    private double averageTokensPerUser;
    private double averageTokensEarnedPerUser;
}