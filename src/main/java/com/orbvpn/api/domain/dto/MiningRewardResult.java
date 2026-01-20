package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class MiningRewardResult {
    private boolean success;
    private BigDecimal amount;
    private BigDecimal newBalance;
    private String message;

}
