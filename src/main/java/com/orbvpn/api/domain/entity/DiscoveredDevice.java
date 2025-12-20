package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.DeviceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a device discovered during a network scan.
 * Stores device information including IP, MAC, vendor, and open ports.
 */
@Entity
@Table(name = "discovered_device", indexes = {
    @Index(name = "idx_dd_scan_id", columnList = "network_scan_id"),
    @Index(name = "idx_dd_user_id", columnList = "user_id"),
    @Index(name = "idx_dd_ip_address", columnList = "ip_address"),
    @Index(name = "idx_dd_mac_address", columnList = "mac_address"),
    @Index(name = "idx_dd_device_type", columnList = "device_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "network_scan_id", nullable = false)
    private NetworkScan networkScan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "mac_address", length = 20)
    private String macAddress;

    @Column(name = "hostname", length = 255)
    private String hostname;

    @Column(name = "vendor", length = 100)
    private String vendor;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 30)
    @Builder.Default
    private DeviceType deviceType = DeviceType.UNKNOWN;

    @Column(name = "custom_name", length = 100)
    private String customName; // User-defined name

    @Column(name = "custom_icon", length = 50)
    private String customIcon; // User-defined icon

    @Column(name = "is_online", nullable = false)
    @Builder.Default
    private Boolean isOnline = false;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "open_ports", length = 2000)
    private String openPorts; // JSON array of port info

    @Column(name = "open_port_count")
    @Builder.Default
    private Integer openPortCount = 0;

    @Column(name = "vulnerability_count")
    @Builder.Default
    private Integer vulnerabilityCount = 0;

    @Column(name = "vulnerabilities", length = 4000)
    private String vulnerabilities; // JSON array of vulnerabilities

    @Column(name = "security_risk_level", length = 20)
    private String securityRiskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "is_gateway", nullable = false)
    @Builder.Default
    private Boolean isGateway = false;

    @Column(name = "os_fingerprint", length = 100)
    private String osFingerprint;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (firstSeenAt == null) {
            firstSeenAt = LocalDateTime.now();
        }
        lastSeenAt = LocalDateTime.now();
    }

    public String getDisplayName() {
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }
        return ipAddress;
    }

    public boolean isVulnerable() {
        return vulnerabilityCount != null && vulnerabilityCount > 0;
    }
}
