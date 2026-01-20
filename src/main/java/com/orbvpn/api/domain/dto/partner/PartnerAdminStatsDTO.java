package com.orbvpn.api.domain.dto.partner;

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
public class PartnerAdminStatsDTO {
    private Integer totalPartners;
    private Integer activePartners;
    private Integer verifiedPartners;
    private Integer totalNodes;
    private Integer onlineNodes;
    private BigDecimal totalBandwidthServedGb;
    private BigDecimal totalRevenueShared;
    private BigDecimal totalTokensEarned;
    private List<TierDistribution> tierDistribution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierDistribution {
        private String tier;
        private Integer count;
        private Integer totalNodes;
        private BigDecimal totalBandwidth;
    }
}
