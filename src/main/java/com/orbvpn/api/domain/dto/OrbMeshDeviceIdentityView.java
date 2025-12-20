package com.orbvpn.api.domain.dto;

import lombok.Data;

@Data
public class OrbMeshDeviceIdentityView {
    private String id;
    private String deviceId;
    private String deviceModel;
    private String manufacturingBatch;
    private String status;
    private String serverId;
    private String serverName;
    private String ownerId;
    private String ownerEmail;
    private String activationIp;
    private String detectedRegion;
    private String detectedCountry;
    private Integer failedAttempts;
    private String createdAt;
    private String activatedAt;
    private String lastSeenAt;
    private String revokedAt;
    private String revocationReason;
}
