package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.Builder;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Data
@Builder
public class StakeTokensInput {
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;

    @Min(1)
    private Integer lockPeriodDays;
}