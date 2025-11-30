package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class MiningSettingsView {
    private Long id;
    private BigDecimal minWithdrawAmount;
    private double rewardRate;
    private boolean autoWithdraw;
    private String withdrawAddress;
    private Boolean notifications;

}
