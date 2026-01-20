package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class DailyStats {
    private int watchedAds;
    private int remainingAds;
    private BigDecimal tokensEarned;
    private BigDecimal tokensSpent;
}