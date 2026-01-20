package com.orbvpn.api.domain.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class AppleSubscriptionData {
  private int groupId;
  private String receipt;
  private LocalDateTime expiresAt;
  private String originalTransactionId;
  private String transactionId;
  private Boolean isTrialPeriod;
  private LocalDateTime trialEndDate;
  private BigDecimal price;
  private String currency;
}
