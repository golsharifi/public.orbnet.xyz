package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Global settings entity for admin-configurable feature flags and system-wide settings.
 * This is a singleton entity - only one row should exist in the database.
 */
@Entity
@Table(name = "global_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Allow users to export WireGuard configurations for third-party clients.
     * When enabled, users can see QR codes and download .conf files to use
     * with the official WireGuard app or other compatible clients.
     */
    @Column(name = "allow_third_party_wireguard_clients", nullable = false)
    @Builder.Default
    private Boolean allowThirdPartyWireGuardClients = false;

    /**
     * Allow users to see their WireGuard private keys.
     * For security, this can be disabled to only show QR codes without revealing keys.
     */
    @Column(name = "show_wireguard_private_keys", nullable = false)
    @Builder.Default
    private Boolean showWireGuardPrivateKeys = true;

    /**
     * Maximum number of WireGuard configs a user can have active at once.
     * Set to 0 for unlimited.
     */
    @Column(name = "max_wireguard_configs_per_user", nullable = false)
    @Builder.Default
    private Integer maxWireGuardConfigsPerUser = 5;

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
