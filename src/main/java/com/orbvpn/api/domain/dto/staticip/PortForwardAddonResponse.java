package com.orbvpn.api.domain.dto.staticip;

import com.orbvpn.api.domain.entity.PortForwardAddon;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortForwardAddonResponse {
    private boolean success;
    private String message;
    private PortForwardAddon addon;
    private String paymentUrl;
}
