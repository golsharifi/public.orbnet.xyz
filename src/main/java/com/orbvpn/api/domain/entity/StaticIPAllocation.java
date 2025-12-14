package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.StaticIPAllocationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a static IP allocated to a user in a specific region.
 * Links to StaticIPPool and StaticIPSubscription.
 */
@Entity
@Table(name = "static_ip_allocation", indexes = {
    @Index(name = "idx_sia_user_id", columnList = "user_id"),
    @Index(name = "idx_sia_public_ip", columnList = "public_ip", unique = true),
    @Index(name = "idx_sia_user_region", columnList = "user_id, region"),
    @Index(name = "idx_sia_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaticIPAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private StaticIPSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ip_pool_id", nullable = false)
    private StaticIPPool ipPool;

    @Column(name = "public_ip", nullable = false, length = 45)
    private String publicIp; // Denormalized for quick access

    @Column(name = "region", nullable = false, length = 50)
    private String region; // Azure region

    @Column(name = "region_display_name", length = 100)
    private String regionDisplayName;

    @Column(name = "internal_ip", length = 45)
    private String internalIp; // WireGuard internal IP mapping

    @Column(name = "server_id")
    private Long serverId; // OrbMesh server this IP is attached to

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StaticIPAllocationStatus status = StaticIPAllocationStatus.PENDING;

    @Column(name = "nat_configured", nullable = false)
    @Builder.Default
    private Boolean natConfigured = false;

    @Column(name = "port_forwards_included", nullable = false)
    @Builder.Default
    private Integer portForwardsIncluded = 1; // Number of port forwards included

    @Column(name = "port_forwards_used", nullable = false)
    @Builder.Default
    private Integer portForwardsUsed = 0; // Number of port forwards in use

    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    @Column(name = "configured_at")
    private LocalDateTime configuredAt; // When NAT was configured

    @Column(name = "last_error", length = 1000)
    private String lastError; // Last error message if configuration failed

    @Column(name = "total_bytes_in")
    @Builder.Default
    private Long totalBytesIn = 0L;

    @Column(name = "total_bytes_out")
    @Builder.Default
    private Long totalBytesOut = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == StaticIPAllocationStatus.ACTIVE;
    }
}
