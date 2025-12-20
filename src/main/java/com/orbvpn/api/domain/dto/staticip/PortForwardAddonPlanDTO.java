package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.PortForwardAddonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortForwardAddonPlanDTO {
    private PortForwardAddonType addonType;
    private String name;
    private int ports;
    private BigDecimal priceMonthly;
}
