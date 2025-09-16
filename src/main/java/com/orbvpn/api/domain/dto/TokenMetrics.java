package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TokenMetrics {
    private BigDecimal totalTokensInCirculation;
    private BigDecimal totalTokensEarned;
    private BigDecimal totalTokensSpent;
    private BigDecimal averageDailyVolume;
    private List<TokenActivityPoint> recentActivity;
}