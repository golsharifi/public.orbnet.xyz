package com.orbvpn.api.domain.dto;

import lombok.Data;

@Data
public class OrbMeshDeviceRegistrationInput {
    private String deviceId;
    private String deviceSecret;
    private String publicIp;
    private String hardwareFingerprint;
    private String deviceName;
    // Optional hardware info
    private String cpuInfo;
    private Integer memoryMb;
    private Integer diskGb;
    private String osVersion;
}
