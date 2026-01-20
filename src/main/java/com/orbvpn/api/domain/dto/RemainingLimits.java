package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class RemainingLimits {
    private int remainingDailyAds;
    private int remainingHourlyAds;
    private LocalDateTime nextHourlyReset;
    private LocalDateTime nextDailyReset;
}