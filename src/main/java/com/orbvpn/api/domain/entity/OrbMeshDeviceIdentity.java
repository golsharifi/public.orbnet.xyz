package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.DeviceProvisioningStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * OrbMesh Device Identity Entity
 *
 * This entity stores pre-provisioned device identities created during manufacturing.
 * Each physical device sold gets a unique device_id and device_secret that is
 * flashed during production. This prevents unauthorized devices from joining the network.
 *
 * Security Model:
 * 1. Manufacturing: Admin generates device identities (device_id + device_secret)
 * 2. Flashing: device_id and device_secret are written to /etc/orbmesh/device-identity.json
 * 3. First boot: Device calls registerOrbMeshDevice with credentials
 * 4. Verification: Backend verifies device_secret matches hash, status is PENDING
 * 5. Activation: Creates OrbMeshServer entry, returns API credentials
 * 6. Binding: Device is marked ACTIVATED, can't be registered again
 */
@Entity
@Table(name = "orbmesh_device_identity", indexes = {
    @Index(name = "idx_odi_device_id", columnList = "device_id", unique = true),
    @Index(name = "idx_odi_status", columnList = "status"),
    @Index(name = "idx_odi_batch", columnList = "manufacturing_batch"),
    @Index(name = "idx_odi_server", columnList = "server_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrbMeshDeviceIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique device identifier (e.g., "OM-2024-A1B2C3")
     * Format: OM-{YEAR}-{UNIQUE_CODE}
     * This is public and can be printed on the device/box
     */
    @Column(name = "device_id", nullable = false, unique = true, length = 32)
    private String deviceId;

    /**
     * BCrypt hash of the device secret
     * The actual secret is only known during manufacturing and is flashed to the device
     * Secret format: 32-byte random hex string
     */
    @Column(name = "device_secret_hash", nullable = false, length = 72)
    private String deviceSecretHash;

    /**
     * Hardware fingerprint for additional verification (optional)
     * Can be: MAC address hash, CPU serial, TPM key, etc.
     * If set during manufacturing, device must provide matching fingerprint
     */
    @Column(name = "hardware_fingerprint", length = 128)
    private String hardwareFingerprint;

    /**
     * Device model/SKU for inventory tracking
     * e.g., "ORBMESH-HOME-1", "ORBMESH-PRO-2"
     */
    @Column(name = "device_model", length = 50)
    private String deviceModel;

    /**
     * Manufacturing batch ID for tracking/recall
     */
    @Column(name = "manufacturing_batch", length = 50)
    private String manufacturingBatch;

    /**
     * Current provisioning status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeviceProvisioningStatus status = DeviceProvisioningStatus.PENDING;

    /**
     * Link to the OrbMeshServer created upon activation
     * Null until device is activated
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id")
    private OrbMeshServer server;

    /**
     * Link to the user who owns this device (optional)
     * Set when user claims the device via app
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    /**
     * IP address from which device was activated
     */
    @Column(name = "activation_ip", length = 45)
    private String activationIp;

    /**
     * Hardware fingerprint provided during activation
     * Stored for audit/security purposes
     */
    @Column(name = "activation_fingerprint", length = 128)
    private String activationFingerprint;

    /**
     * Geographic region detected during activation
     */
    @Column(name = "detected_region", length = 50)
    private String detectedRegion;

    /**
     * Country code detected from IP during activation
     */
    @Column(name = "detected_country", length = 2)
    private String detectedCountry;

    /**
     * Number of failed registration attempts (for rate limiting)
     */
    @Column(name = "failed_attempts")
    @Builder.Default
    private Integer failedAttempts = 0;

    /**
     * Last failed attempt timestamp (for rate limiting)
     */
    @Column(name = "last_failed_attempt")
    private LocalDateTime lastFailedAttempt;

    /**
     * Reason for revocation (if status is REVOKED)
     */
    @Column(name = "revocation_reason", length = 255)
    private String revocationReason;

    /**
     * Admin who revoked the device
     */
    @Column(name = "revoked_by", length = 100)
    private String revokedBy;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (status == null) status = DeviceProvisioningStatus.PENDING;
        if (failedAttempts == null) failedAttempts = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if device can be registered (activated)
     */
    public boolean canRegister() {
        return status == DeviceProvisioningStatus.PENDING;
    }

    /**
     * Check if device is currently active
     */
    public boolean isActive() {
        return status == DeviceProvisioningStatus.ACTIVATED;
    }

    /**
     * Check if rate limit should be applied (too many failed attempts)
     */
    public boolean isRateLimited() {
        if (failedAttempts == null || failedAttempts < 5) {
            return false;
        }
        if (lastFailedAttempt == null) {
            return false;
        }
        // Rate limit: 5 failed attempts = 15 minute lockout
        return lastFailedAttempt.plusMinutes(15).isAfter(LocalDateTime.now());
    }

    /**
     * Record a failed registration attempt
     */
    public void recordFailedAttempt() {
        this.failedAttempts = (this.failedAttempts == null ? 0 : this.failedAttempts) + 1;
        this.lastFailedAttempt = LocalDateTime.now();
    }

    /**
     * Activate the device
     */
    public void activate(String ip, String fingerprint, String region, String country) {
        this.status = DeviceProvisioningStatus.ACTIVATED;
        this.activatedAt = LocalDateTime.now();
        this.activationIp = ip;
        this.activationFingerprint = fingerprint;
        this.detectedRegion = region;
        this.detectedCountry = country;
        this.failedAttempts = 0; // Reset on successful activation
    }

    /**
     * Revoke the device
     */
    public void revoke(String reason, String adminEmail) {
        this.status = DeviceProvisioningStatus.REVOKED;
        this.revokedAt = LocalDateTime.now();
        this.revocationReason = reason;
        this.revokedBy = adminEmail;
    }
}
