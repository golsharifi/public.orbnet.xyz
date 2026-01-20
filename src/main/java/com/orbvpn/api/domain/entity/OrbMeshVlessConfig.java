package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * VLESS configuration for OrbMesh protocol.
 * Unlike WireGuard, VLESS uses UUIDs for user identification
 * and doesn't require IP allocation (it's a proxy, not a tunnel).
 */
@Entity
@Table(name = "orbmesh_vless_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrbMeshVlessConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    /**
     * VLESS protocol UUID - this is what the client uses to authenticate
     * with the sing-box server. Different from userUuid which is OrbNet's user ID.
     */
    @Column(name = "vless_uuid", nullable = false, length = 36)
    private String vlessUuid;

    /**
     * Flow control type for XTLS optimization
     * Common values: "xtls-rprx-vision", "" (empty for none)
     */
    @Column(name = "flow", length = 50)
    @Builder.Default
    private String flow = "xtls-rprx-vision";

    /**
     * Encryption method - VLESS uses "none" as it relies on external TLS
     */
    @Column(name = "encryption", length = 20)
    @Builder.Default
    private String encryption = "none";

    /**
     * Security type: "reality", "tls", "none"
     */
    @Column(name = "security", length = 20)
    @Builder.Default
    private String security = "reality";

    /**
     * Transport type: "tcp", "ws", "grpc", "quic"
     */
    @Column(name = "transport", length = 20)
    @Builder.Default
    private String transport = "tcp";

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private OrbMeshServer server;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
        if (flow == null) {
            flow = "xtls-rprx-vision";
        }
        if (encryption == null) {
            encryption = "none";
        }
        if (security == null) {
            security = "reality";
        }
        if (transport == null) {
            transport = "tcp";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getIsActive() {
        return active;
    }

    public void setIsActive(Boolean active) {
        this.active = active;
    }
}
