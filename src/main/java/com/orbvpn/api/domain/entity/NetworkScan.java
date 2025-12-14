package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.NetworkScanStatus;
import com.orbvpn.api.domain.enums.NetworkScanType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a network scan performed by a user through VPN tunnel.
 * Stores scan results including discovered devices and security score.
 */
@Entity
@Table(name = "network_scan", indexes = {
    @Index(name = "idx_ns_user_id", columnList = "user_id"),
    @Index(name = "idx_ns_created_at", columnList = "created_at"),
    @Index(name = "idx_ns_status", columnList = "status"),
    @Index(name = "idx_ns_user_network", columnList = "user_id, network_cidr")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_id", nullable = false, unique = true, length = 64)
    private String scanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "network_cidr", nullable = false, length = 30)
    private String networkCidr; // e.g., "192.168.1.0/24"

    @Column(name = "gateway_ip", length = 45)
    private String gatewayIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false, length = 20)
    @Builder.Default
    private NetworkScanType scanType = NetworkScanType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NetworkScanStatus status = NetworkScanStatus.PENDING;

    @Column(name = "total_devices")
    @Builder.Default
    private Integer totalDevices = 0;

    @Column(name = "online_devices")
    @Builder.Default
    private Integer onlineDevices = 0;

    @Column(name = "vulnerable_devices")
    @Builder.Default
    private Integer vulnerableDevices = 0;

    @Column(name = "security_score")
    private Integer securityScore; // 0-100

    @Column(name = "security_grade", length = 5)
    private String securityGrade; // A+, A, B, C, D, F

    @OneToMany(mappedBy = "networkScan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DiscoveredDevice> devices = new ArrayList<>();

    @Column(name = "server_id")
    private Long serverId; // OrbMesh server that performed the scan

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (scanId == null) {
            scanId = "scan_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        }
    }

    public void addDevice(DiscoveredDevice device) {
        devices.add(device);
        device.setNetworkScan(this);
    }

    public void removeDevice(DiscoveredDevice device) {
        devices.remove(device);
        device.setNetworkScan(null);
    }

    public void calculateStats() {
        if (devices != null) {
            this.totalDevices = devices.size();
            this.onlineDevices = (int) devices.stream().filter(DiscoveredDevice::getIsOnline).count();
            this.vulnerableDevices = (int) devices.stream()
                .filter(d -> d.getVulnerabilityCount() != null && d.getVulnerabilityCount() > 0)
                .count();
        }
    }
}
