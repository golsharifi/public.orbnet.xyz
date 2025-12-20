package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orbmesh_connection_stats", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_server_id", columnList = "server_id"),
        @Index(name = "idx_session_id", columnList = "session_id"),
        @Index(name = "idx_connected_at", columnList = "connected_at"),
        @Index(name = "idx_protocol", columnList = "protocol"),
        @Index(name = "idx_vpn_protocol", columnList = "vpn_protocol")
})
public class OrbMeshConnectionStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private OrbMeshServer server;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "bytes_sent", nullable = false)
    private Long bytesSent = 0L;

    @Column(name = "bytes_received", nullable = false)
    private Long bytesReceived = 0L;

    @Column(name = "duration", nullable = false)
    private Integer duration = 0;

    @Column(name = "protocol", nullable = false)
    private String protocol;

    @Column(name = "vpn_protocol")
    private String vpnProtocol;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Column(name = "disconnect_reason")
    private String disconnectReason;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "client_version")
    private String clientVersion;

    @Column(name = "client_platform")
    private String clientPlatform;

    @Column(name = "average_latency_ms")
    private Integer averageLatencyMs;

    @Column(name = "packet_loss_percent")
    private Double packetLossPercent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getTotalBandwidth() {
        return bytesSent + bytesReceived;
    }

    public Double getTotalBandwidthMB() {
        return getTotalBandwidth() / (1024.0 * 1024.0);
    }

    public Double getTotalBandwidthGB() {
        return getTotalBandwidth() / (1024.0 * 1024.0 * 1024.0);
    }
}
