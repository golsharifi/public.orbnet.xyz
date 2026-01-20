package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class OrbMeshDeviceRegistrationResult {
    private Boolean success;
    private String message;
    private String serverId;
    private String serverName;
    private String apiKey;
    private String jwtSecret;
    private String region;
    private OrbMeshDeviceCertificate certificate;
    private List<String> dnsServers;
    private List<String> ntpServers;
}
