package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.StaticIPPlanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStatsDTO {
    private StaticIPPlanType planType;
    private int count;
    private BigDecimal revenue;
}
