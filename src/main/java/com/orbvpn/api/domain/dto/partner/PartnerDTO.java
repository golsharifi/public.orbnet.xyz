package com.orbvpn.api.domain.dto.partner;

import com.orbvpn.api.domain.enums.PartnerTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerDTO {
    private Long id;
    private String partnerUuid;
    private String partnerName;
    private String contactEmail;
    private String contactPhone;
    private String companyName;
    private String countryCode;
    private PartnerTier tier;
    private BigDecimal revenueSharePercent;
    private BigDecimal tokenBonusMultiplier;
    private LocalDateTime agreementSignedAt;
    private LocalDateTime agreementExpiresAt;
    private Boolean isVerified;
    private Boolean isActive;
    private Integer totalNodes;
    private Integer totalStaticIps;
    private BigDecimal totalBandwidthServedGb;
    private BigDecimal totalRevenueEarned;
    private BigDecimal totalTokensEarned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
