package com.orbvpn.api.domain.dto.partner;

import lombok.Data;

@Data
public class RegisterNodeInput {
    private String publicIp;
    private String ddnsHostname;
    private String region;
    private String regionDisplayName;
    private String countryCode;
    private Boolean hasStaticIp;
    private Boolean supportsPortForward;
    private Boolean supportsBridgeNode;
    private Boolean supportsAi;
    private Boolean isBehindCgnat;
    private Boolean canEarnTokens;
    private Integer uploadMbps;
    private Integer downloadMbps;
    private Integer maxConnections;
    private Integer cpuCores;
    private Integer ramMb;
    private String deviceType;
    private String softwareVersion;
}
