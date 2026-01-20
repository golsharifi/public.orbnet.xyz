package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.DeploymentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unified OrbMesh node table representing all deployment types:
 * - OrbVPN managed datacenter nodes
 * - Partner datacenter nodes
 * - User home device nodes
 */
@Entity
@Table(name = "orbmesh_node", indexes = {
    @Index(name = "idx_omn_node_uuid", columnList = "node_uuid", unique = true),
    @Index(name = "idx_omn_online", columnList = "online"),
    @Index(name = "idx_omn_deployment", columnList = "deployment_type"),
    @Index(name = "idx_omn_partner", columnList = "partner_id"),
    @Index(name = "idx_omn_region", columnList = "region"),
    @Index(name = "idx_omn_owner", columnList = "owner_user_id"),
    @Index(name = "idx_omn_capabilities", columnList = "has_static_ip, supports_port_forward, supports_bridge_node")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrbMeshNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_uuid", nullable = false, unique = true, length = 36)
    private String nodeUuid;

    // Ownership
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser; // NULL for orbvpn_dc, user_id for home

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private OrbMeshPartner partner; // Set for partner_dc nodes

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_type", nullable = false, length = 20)
    private DeploymentType deploymentType;

    // Network
    @Column(name = "public_ip", length = 45)
    private String publicIp;

    @Column(name = "ddns_hostname", length = 255)
    private String ddnsHostname;

    @Column(name = "region", length = 50)
    private String region; // Azure region or partner location

    @Column(name = "region_display_name", length = 100)
    private String regionDisplayName;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    // Capabilities (reported by node)
    @Column(name = "has_static_ip", nullable = false)
    @Builder.Default
    private Boolean hasStaticIp = false;

    @Column(name = "supports_port_forward", nullable = false)
    @Builder.Default
    private Boolean supportsPortForward = false;

    @Column(name = "supports_bridge_node", nullable = false)
    @Builder.Default
    private Boolean supportsBridgeNode = false;

    @Column(name = "supports_ai", nullable = false)
    @Builder.Default
    private Boolean supportsAi = false;

    @Column(name = "is_behind_cgnat", nullable = false)
    @Builder.Default
    private Boolean isBehindCgnat = false;

    @Column(name = "can_earn_tokens", nullable = false)
    @Builder.Default
    private Boolean canEarnTokens = false;

    // Static IP support
    @Column(name = "supports_static_ip", nullable = false)
    @Builder.Default
    private Boolean supportsStaticIp = false;

    @Column(name = "static_ips_available")
    @Builder.Default
    private Integer staticIpsAvailable = 0;

    @Column(name = "static_ips_used")
    @Builder.Default
    private Integer staticIpsUsed = 0;

    // API endpoint
    @Column(name = "api_port")
    @Builder.Default
    private Integer apiPort = 8080;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Performance
    @Column(name = "upload_mbps")
    @Builder.Default
    private Integer uploadMbps = 0;

    @Column(name = "download_mbps")
    @Builder.Default
    private Integer downloadMbps = 0;

    @Column(name = "max_connections")
    @Builder.Default
    private Integer maxConnections = 100;

    // Hardware
    @Column(name = "cpu_cores")
    private Integer cpuCores;

    @Column(name = "ram_mb")
    private Integer ramMb;

    @Column(name = "device_type", length = 50)
    private String deviceType; // 'azure_vm', 'dedicated', 'raspberry_pi', etc.

    @Column(name = "software_version", length = 20)
    private String softwareVersion;

    // Status
    @Column(name = "online", nullable = false)
    @Builder.Default
    private Boolean online = false;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "uptime_percentage", precision = 5, scale = 2)
    private BigDecimal uptimePercentage;

    @Column(name = "current_connections")
    @Builder.Default
    private Integer currentConnections = 0;

    // Token mining (partner_dc and home only)
    @Column(name = "is_mining_enabled", nullable = false)
    @Builder.Default
    private Boolean isMiningEnabled = false;

    @Column(name = "total_bandwidth_served_gb", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalBandwidthServedGb = BigDecimal.ZERO;

    @Column(name = "total_tokens_earned", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTokensEarned = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isDatacenter() {
        return deploymentType == DeploymentType.ORBVPN_DC ||
               deploymentType == DeploymentType.PARTNER_DC;
    }

    /**
     * Alias for getPublicIp() for convenience
     */
    public String getIpAddress() {
        return publicIp;
    }

    /**
     * Alias for getOnline() for convenience
     */
    public Boolean getIsOnline() {
        return online;
    }
}
