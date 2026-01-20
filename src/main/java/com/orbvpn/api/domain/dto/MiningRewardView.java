package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MiningRewardView {
    private Long id;
    private MiningServerView server;
    private BigDecimal amount;
    private LocalDateTime rewardTime;
    private String transactionHash;
}