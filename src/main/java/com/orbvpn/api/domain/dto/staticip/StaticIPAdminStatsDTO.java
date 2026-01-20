package com.orbvpn.api.domain.dto.staticip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticIPAdminStatsDTO {
    private int totalSubscriptions;
    private int activeSubscriptions;
    private int totalAllocations;
    private int activeAllocations;
    private BigDecimal totalRevenue;
    private List<StaticIPPoolStatsDTO> poolStats;
    private List<PlanStatsDTO> subscriptionsByPlan;
}
