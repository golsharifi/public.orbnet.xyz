package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.enums.PortForwardAddonType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePortForwardAddonInput {
    private Long allocationId;
    private PortForwardAddonType addonType;
    private String paymentMethod;
    private String selectedCoin;
}
