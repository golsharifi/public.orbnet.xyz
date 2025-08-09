package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GooglePlaySubscriptionInfo {
    private Integer groupId;
    private LocalDateTime expiresAt;
    private String purchaseToken;
    private String orderId;
    private Boolean isTrialPeriod;

}
