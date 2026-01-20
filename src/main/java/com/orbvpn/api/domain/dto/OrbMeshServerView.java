// src/main/java/com/orbvpn/api/domain/dto/OrbMeshServerView.java
package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrbMeshServerView {
    private Long id;
    private String name;
    private String region;
    private String hostname;
    private String ipAddress;
    private Integer port;
    private String location;
    private String country;
    private String countryCode;
    private List<String> protocols; // ✅ List of protocols
    private Boolean quantumSafe;
    private Boolean online;
    private Boolean enabled;
    private Integer currentConnections;
    private Integer maxConnections;
    private Integer bandwidthLimitMbps;
    private Double cpuUsage;
    private Double memoryUsage;
    private Integer latencyMs;
    private String version;
    private String publicKey;
    private Integer wireguardPort;
    private String wireguardPublicKey;
    private Integer vlessPort;
    private String realityPublicKey;
    private String realitySNI;
    private String tlsFingerprint;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}