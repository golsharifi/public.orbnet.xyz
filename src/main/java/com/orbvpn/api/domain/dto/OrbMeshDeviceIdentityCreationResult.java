package com.orbvpn.api.domain.dto;

import lombok.Data;

@Data
public class OrbMeshDeviceIdentityCreationResult {
    private String deviceId;
    private String deviceSecret;  // Only returned once during manufacturing!
    private OrbMeshDeviceIdentityView identity;
}
