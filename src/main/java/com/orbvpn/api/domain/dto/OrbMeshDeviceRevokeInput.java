package com.orbvpn.api.domain.dto;

import lombok.Data;

@Data
public class OrbMeshDeviceRevokeInput {
    private String deviceId;
    private String reason;
}
