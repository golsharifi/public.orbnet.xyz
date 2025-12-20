package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.DeviceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Crowdsourced device fingerprint data.
 * Stores aggregated information about devices identified by various characteristics.
 * Used for device identification when MAC lookup alone is insufficient.
 */
@Entity
@Table(name = "device_fingerprint", indexes = {
    @Index(name = "idx_fp_mac_prefix", columnList = "mac_prefix"),
    @Index(name = "idx_fp_port_signature", columnList = "port_signature"),
    @Index(name = "idx_fp_device_type", columnList = "device_type"),
    @Index(name = "idx_fp_hostname_pattern", columnList = "hostname_pattern"),
    @Index(name = "idx_fp_mdns_service", columnList = "mdns_service_type"),
    @Index(name = "idx_fp_ssdp_server", columnList = "ssdp_server_pattern")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * MAC address prefix (OUI) if known.
     */
    @Column(name = "mac_prefix", length = 6)
    private String macPrefix;

    /**
     * Port signature - sorted list of commonly open ports.
     * Example: "22,80,443,8080"
     */
    @Column(name = "port_signature", length = 200)
    private String portSignature;

    /**
     * Hostname pattern (regex) for matching.
     * Example: ".*-iPhone$", "DESKTOP-.*"
     */
    @Column(name = "hostname_pattern", length = 100)
    private String hostnamePattern;

    /**
     * SSDP SERVER header pattern for matching.
     * Example: ".*Hue.*IpBridge.*"
     */
    @Column(name = "ssdp_server_pattern", length = 200)
    private String ssdpServerPattern;

    /**
     * UPnP device type URN.
     * Example: "urn:schemas-upnp-org:device:InternetGatewayDevice:1"
     */
    @Column(name = "upnp_device_type", length = 200)
    private String upnpDeviceType;

    /**
     * mDNS service type that identifies this device.
     * Example: "_airplay._tcp", "_googlecast._tcp"
     */
    @Column(name = "mdns_service_type", length = 100)
    private String mdnsServiceType;

    /**
     * HTTP banner/server header pattern.
     * Example: "nginx.*", "Apache.*"
     */
    @Column(name = "http_banner_pattern", length = 200)
    private String httpBannerPattern;

    /**
     * TTL value from ICMP/TCP (for OS fingerprinting).
     * Windows: 128, Linux: 64, Cisco: 255
     */
    @Column(name = "ttl_value")
    private Integer ttlValue;

    /**
     * TCP window size (for OS fingerprinting).
     */
    @Column(name = "tcp_window_size")
    private Integer tcpWindowSize;

    /**
     * Identified device type.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 30)
    private DeviceType deviceType;

    /**
     * Specific device model if known.
     * Example: "iPhone 14 Pro", "Samsung Galaxy S23"
     */
    @Column(name = "device_model", length = 100)
    private String deviceModel;

    /**
     * Manufacturer name.
     */
    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    /**
     * Operating system.
     */
    @Column(name = "operating_system", length = 50)
    private String operatingSystem;

    /**
     * Confidence score (0-100) for this fingerprint.
     */
    @Column(name = "confidence_score")
    @Builder.Default
    private Integer confidenceScore = 50;

    /**
     * Number of times this fingerprint matched a device.
     */
    @Column(name = "match_count")
    @Builder.Default
    private Long matchCount = 0L;

    /**
     * Number of unique users who confirmed this fingerprint.
     */
    @Column(name = "confirmed_by_users")
    @Builder.Default
    private Long confirmedByUsers = 0L;

    /**
     * Priority for matching (higher = checked first).
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * Is this fingerprint active/enabled?
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Notes about this fingerprint.
     */
    @Column(name = "notes", length = 500)
    private String notes;

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

    /**
     * Check if this fingerprint matches the given device characteristics.
     */
    public boolean matches(String mac, String ports, String hostname, String ssdpServer, String mdnsService) {
        int matchScore = 0;
        int totalChecks = 0;

        // MAC prefix match
        if (macPrefix != null && mac != null) {
            totalChecks++;
            String deviceOui = mac.replaceAll("[:-]", "").substring(0, Math.min(6, mac.length())).toUpperCase();
            if (deviceOui.startsWith(macPrefix)) {
                matchScore += 30;
            }
        }

        // Port signature match
        if (portSignature != null && ports != null) {
            totalChecks++;
            if (portsMatch(portSignature, ports)) {
                matchScore += 25;
            }
        }

        // Hostname pattern match
        if (hostnamePattern != null && hostname != null) {
            totalChecks++;
            try {
                if (hostname.matches(hostnamePattern)) {
                    matchScore += 20;
                }
            } catch (Exception e) {
                // Invalid regex
            }
        }

        // SSDP server match
        if (ssdpServerPattern != null && ssdpServer != null) {
            totalChecks++;
            try {
                if (ssdpServer.matches(ssdpServerPattern)) {
                    matchScore += 20;
                }
            } catch (Exception e) {
                // Invalid regex
            }
        }

        // mDNS service match
        if (mdnsServiceType != null && mdnsService != null) {
            totalChecks++;
            if (mdnsService.contains(mdnsServiceType)) {
                matchScore += 25;
            }
        }

        // Consider it a match if score is above threshold
        return totalChecks > 0 && matchScore >= 20;
    }

    private boolean portsMatch(String signature, String devicePorts) {
        String[] sigPorts = signature.split(",");
        String[] devPorts = devicePorts.split(",");

        int matches = 0;
        for (String sigPort : sigPorts) {
            for (String devPort : devPorts) {
                if (sigPort.trim().equals(devPort.trim())) {
                    matches++;
                    break;
                }
            }
        }

        // At least 50% of signature ports should be present
        return matches >= sigPorts.length / 2;
    }
}
