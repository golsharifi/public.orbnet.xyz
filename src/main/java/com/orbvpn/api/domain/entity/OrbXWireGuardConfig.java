package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orbx_wireguard_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrbXWireGuardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    // ✅ REMOVED the standalone serverId field - we'll use the relationship only
    // @Column(name = "server_id", nullable = false)
    // private Long serverId;

    @Column(name = "private_key", nullable = false, length = 44)
    private String privateKey;

    @Column(name = "public_key", nullable = false, length = 44)
    private String publicKey;

    @Column(name = "allocated_ip", nullable = false, length = 45)
    private String allocatedIp;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    // ✅ FIXED: Only use the @ManyToOne relationship - it manages the server_id
    // column
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private OrbXServer server;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ✅ Convenience methods for Lombok compatibility
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