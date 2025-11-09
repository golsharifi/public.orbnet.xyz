// src/main/java/com/orbvpn/api/domain/entity/OrbXConnectionStats.java
package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orbx_connection_stats", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_server_id", columnList = "server_id"),
        @Index(name = "idx_session_id", columnList = "session_id"),
        @Index(name = "idx_connected_at", columnList = "connected_at"),
        @Index(name = "idx_protocol", columnList = "protocol")
})
public class OrbXConnectionStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private OrbXServer server;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "bytes_sent", nullable = false)
    private Long bytesSent = 0L;

    @Column(name = "bytes_received", nullable = false)
    private Long bytesReceived = 0L;

    @Column(name = "duration", nullable = false)
    private Integer duration = 0; // Duration in seconds

    @Column(name = "protocol", nullable = false)
    private String protocol; // "teams", "shaparak", "doh", "https"

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Column(name = "disconnect_reason")
    private String disconnectReason; // "user", "timeout", "error", "server"

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "client_version")
    private String clientVersion;

    @Column(name = "client_platform")
    private String clientPlatform; // "ios", "android", "windows", "macos", "linux"

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

    // Helper method to calculate total bandwidth
    public Long getTotalBandwidth() {
        return bytesSent + bytesReceived;
    }

    // Helper method to calculate bandwidth in MB
    public Double getTotalBandwidthMB() {
        return getTotalBandwidth() / (1024.0 * 1024.0);
    }

    // Helper method to calculate bandwidth in GB
    public Double getTotalBandwidthGB() {
        return getTotalBandwidth() / (1024.0 * 1024.0 * 1024.0);
    }
}