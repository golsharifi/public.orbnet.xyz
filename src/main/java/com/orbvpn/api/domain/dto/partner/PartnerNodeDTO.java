package com.orbvpn.api.domain.dto.partner;

import com.orbvpn.api.domain.enums.DeploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerNodeDTO {
    private Long id;
    private String nodeUuid;
    private Long partnerId;
    private String partnerName;
    private DeploymentType deploymentType;
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
    private Boolean online;
    private LocalDateTime lastHeartbeat;
    private BigDecimal uptimePercentage;
    private Integer currentConnections;
    private Boolean isMiningEnabled;
    private BigDecimal totalBandwidthServedGb;
    private BigDecimal totalTokensEarned;
    private LocalDateTime createdAt;
}
