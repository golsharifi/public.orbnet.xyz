package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class UserTokenStats {
    private Integer userId;
    private String username;
    private String email;
    private BigDecimal currentBalance;
    private int totalAdsWatched;
    private int adsWatchedToday;
    private BigDecimal tokensEarnedToday;
    private BigDecimal tokensSpentToday;
    private LocalDateTime lastActivity;
    private boolean isActive;
    private LocalDateTime subscriptionStart;
    private LocalDateTime subscriptionEnd;
}