package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class OrbMeshConfig {
    private Long serverId;
    private String endpoint;
    private Integer port;
    private String publicKey;
    private List<String> protocols;
    private String tlsFingerprint;
    private Boolean quantumSafe;
    private String region;
}