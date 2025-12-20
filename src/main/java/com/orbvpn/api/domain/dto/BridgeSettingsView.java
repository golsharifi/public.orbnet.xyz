package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BridgeSettingsView {
    private Boolean enabled;
    private Long selectedBridgeId;
    private Boolean autoSelect;
    private Long lastUsedBridgeId;
}
