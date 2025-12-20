package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents available static public IPs in the pool.
 * These are Azure Standard SKU Public IPs that can be allocated to users.
 */
@Entity
@Table(name = "static_ip_pool", indexes = {
    @Index(name = "idx_sip_region_available", columnList = "region, is_allocated"),
    @Index(name = "idx_sip_public_ip", columnList = "public_ip", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaticIPPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_ip", nullable = false, unique = true, length = 45)
    private String publicIp; // IPv4 or IPv6

    @Column(name = "region", nullable = false, length = 50)
    private String region; // Azure region: eastus, westeurope, etc.

    @Column(name = "region_display_name", length = 100)
    private String regionDisplayName; // "US East", "EU West", etc.

    @Column(name = "azure_resource_id", length = 500)
    private String azureResourceId; // Azure resource reference

    @Column(name = "azure_subscription_id", length = 100)
    private String azureSubscriptionId;

    @Column(name = "server_id")
    private Long serverId; // OrbMesh server this IP is attached to

    @Column(name = "is_allocated", nullable = false)
    @Builder.Default
    private Boolean isAllocated = false;

    @Column(name = "allocated_to_user_id")
    private Integer allocatedToUserId;

    @Column(name = "cost_per_month", precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal costPerMonth = new java.math.BigDecimal("3.60");

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "allocated_at")
    private LocalDateTime allocatedAt;

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
