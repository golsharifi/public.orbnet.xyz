package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stores MAC address OUI (Organizationally Unique Identifier) entries.
 * The first 3 bytes (6 hex characters) of a MAC address identify the vendor.
 * Data is sourced from IEEE OUI database and enriched with crowdsourced data.
 */
@Entity
@Table(name = "mac_oui_entry", indexes = {
    @Index(name = "idx_oui_prefix", columnList = "oui_prefix", unique = true),
    @Index(name = "idx_oui_vendor", columnList = "vendor_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MacOuiEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * OUI prefix - first 6 hex characters of MAC address (without colons).
     * Example: "AABBCC" from MAC "AA:BB:CC:DD:EE:FF"
     */
    @Column(name = "oui_prefix", nullable = false, length = 6, unique = true)
    private String ouiPrefix;

    /**
     * Vendor/Manufacturer name from IEEE registry.
     */
    @Column(name = "vendor_name", nullable = false, length = 100)
    private String vendorName;

    /**
     * Short vendor name for display.
     */
    @Column(name = "vendor_short", length = 50)
    private String vendorShort;

    /**
     * Country of the vendor.
     */
    @Column(name = "country", length = 50)
    private String country;

    /**
     * Assignment type: MA-L (large), MA-M (medium), MA-S (small), IAB.
     */
    @Column(name = "assignment_type", length = 10)
    private String assignmentType;

    /**
     * Most common device type for this OUI.
     */
    @Column(name = "common_device_type", length = 30)
    private String commonDeviceType;

    /**
     * Number of times this OUI has been seen.
     */
    @Column(name = "seen_count")
    @Builder.Default
    private Long seenCount = 0L;

    /**
     * Source of the entry: IEEE, CROWDSOURCED, MANUAL.
     */
    @Column(name = "source", length = 20)
    @Builder.Default
    private String source = "IEEE";

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
     * Normalize MAC to OUI prefix format.
     */
    public static String macToOuiPrefix(String mac) {
        if (mac == null) return null;
        return mac.replaceAll("[:-]", "").substring(0, 6).toUpperCase();
    }
}
