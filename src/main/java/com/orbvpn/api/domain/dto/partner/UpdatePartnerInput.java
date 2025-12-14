package com.orbvpn.api.domain.dto.partner;

import com.orbvpn.api.domain.enums.PartnerTier;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePartnerInput {
    private Long partnerId;
    private String partnerName;
    private String contactEmail;
    private String contactPhone;
    private String companyName;
    private String countryCode;
    private PartnerTier tier;
    private BigDecimal revenueSharePercent;
    private BigDecimal tokenBonusMultiplier;
    private Boolean isActive;
    private Boolean isVerified;
}
