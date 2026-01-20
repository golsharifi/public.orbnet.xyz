package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MiningSettingsInput {
    private String withdrawAddress;
    private BigDecimal minWithdrawAmount;
    private Boolean autoWithdraw;
    private Boolean notifications;
}
