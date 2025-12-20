package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * OrbMesh Home Device Entity
 * Additional configuration and settings for home device deployments.
 */
@Entity
@Table(name = "orbmesh_home_device", indexes = {
    @Index(name = "idx_ohd_user", columnList = "user_id"),
    @Index(name = "idx_ohd_setup", columnList = "setup_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbMeshHomeDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, unique = true)
    private OrbMeshNode node;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Device setup
    @Column(name = "setup_code", length = 20)
    private String setupCode;

    @Column(name = "setup_completed_at")
    private LocalDateTime setupCompletedAt;

    // DDNS configuration
    @Column(name = "ddns_enabled")
    @Builder.Default
    private Boolean ddnsEnabled = false;

    @Column(name = "ddns_provider", length = 50)
    private String ddnsProvider;

    @Column(name = "ddns_hostname")
    private String ddnsHostname;

    @Column(name = "ddns_last_update")
    private LocalDateTime ddnsLastUpdate;

    @Column(name = "ddns_last_ip", length = 45)
    private String ddnsLastIp;

    // Router/NAT configuration
    @Column(name = "upnp_port_mapped")
    @Builder.Default
    private Boolean upnpPortMapped = false;

    @Column(name = "upnp_external_port")
    private Integer upnpExternalPort;

    @Column(name = "manual_port_forward_instructions", columnDefinition = "TEXT")
    private String manualPortForwardInstructions;

    // User preferences
    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "max_guest_connections")
    @Builder.Default
    private Integer maxGuestConnections = 5;

    // Timestamps
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
