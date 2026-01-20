package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orbmesh_wireguard_ip_pools")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrbMeshWireGuardIPPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "orbmesh_server_id", nullable = false, unique = true)
    private Long orbmeshServerId;

    @Column(name = "cidr", nullable = false, length = 18)
    private String cidr;

    @Column(name = "gateway_ip", nullable = false, length = 15)
    private String gatewayIp;

    @Column(name = "next_available_ip", nullable = false, length = 15)
    private String nextAvailableIp;

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
