package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.PortForwardProtocol;
import com.orbvpn.api.domain.enums.PortForwardStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a port forwarding rule for a static IP allocation.
 * Maps an external port on the static IP to an internal port on the user's device.
 */
@Entity
@Table(name = "port_forward_rule", indexes = {
    @Index(name = "idx_pfr_allocation_id", columnList = "allocation_id"),
    @Index(name = "idx_pfr_user_id", columnList = "user_id"),
    @Index(name = "idx_pfr_external_port", columnList = "allocation_id, external_port, protocol", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortForwardRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    private StaticIPAllocation allocation;

    @Column(name = "name", length = 100)
    private String name; // User-friendly name: "Home SSH", "Minecraft Server"

    @Column(name = "description", length = 500)
    private String description; // User-provided description

    @Column(name = "external_port", nullable = false)
    private Integer externalPort; // Port on the static IP

    @Column(name = "internal_port", nullable = false)
    private Integer internalPort; // Port on user's device

    @Column(name = "internal_ip", length = 45)
    private String internalIp; // User's device internal IP (optional, defaults to VPN IP)

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 10)
    @Builder.Default
    private PortForwardProtocol protocol = PortForwardProtocol.TCP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PortForwardStatus status = PortForwardStatus.PENDING;

    @Column(name = "is_from_addon", nullable = false)
    @Builder.Default
    private Boolean isFromAddon = false; // True if from purchased addon pack

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addon_id")
    private PortForwardAddon addon; // If from addon pack

    // Source IP whitelist (comma-separated CIDR blocks, null = allow all)
    @Column(name = "source_ip_whitelist", length = 1000)
    private String sourceIpWhitelist;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "rule_configured", nullable = false)
    @Builder.Default
    private Boolean ruleConfigured = false; // NAT rule applied on server

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "configured_at")
    private LocalDateTime configuredAt; // When the NAT rule was applied

    @Column(name = "last_error", length = 1000)
    private String lastError; // Last error message if configuration failed

    @Column(name = "total_connections")
    @Builder.Default
    private Long totalConnections = 0L;

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
}
