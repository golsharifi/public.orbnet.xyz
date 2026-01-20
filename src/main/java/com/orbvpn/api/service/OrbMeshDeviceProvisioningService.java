package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.OrbMeshDeviceIdentity;
import com.orbvpn.api.domain.entity.OrbMeshServer;
import com.orbvpn.api.domain.enums.DeviceProvisioningStatus;
import com.orbvpn.api.repository.OrbMeshDeviceIdentityRepository;
import com.orbvpn.api.repository.OrbMeshServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Service for secure OrbMesh device provisioning.
 *
 * This service handles:
 * 1. Manufacturing: Creating device identities with secrets
 * 2. Registration: Verifying device credentials and activating devices
 * 3. Management: Revoking, deactivating, and tracking devices
 *
 * Security features:
 * - BCrypt hashing for device secrets
 * - Rate limiting on failed attempts
 * - Hardware fingerprint binding (optional)
 * - Audit logging for all operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrbMeshDeviceProvisioningService {

    private final OrbMeshDeviceIdentityRepository deviceIdentityRepository;
    private final OrbMeshServerRepository serverRepository;
    private final OrbMeshServerService serverService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private final SecureRandom secureRandom = new SecureRandom();

    // Device ID format: OM-{YEAR}-{6_CHAR_CODE}
    private static final String DEVICE_ID_PREFIX = "OM";
    private static final int DEVICE_SECRET_LENGTH = 32; // 32 bytes = 64 hex chars

    /**
     * Result of device identity generation (for manufacturing)
     */
    public record DeviceIdentityCreationResult(
            String deviceId,
            String deviceSecret,  // Plain text - only returned once during manufacturing
            OrbMeshDeviceIdentity identity
    ) {}

    /**
     * Result of device registration (activation)
     */
    public record DeviceRegistrationResult(
            boolean success,
            String message,
            OrbMeshServer server,
            String apiKey,      // Only returned on successful registration
            String jwtSecret    // Only returned on successful registration
    ) {}

    /**
     * Generate a batch of device identities for manufacturing.
     *
     * @param count Number of devices to generate
     * @param model Device model/SKU
     * @param batch Manufacturing batch ID
     * @param hardwareFingerprints Optional list of hardware fingerprints (must match count if provided)
     * @return List of created device identities with plain text secrets
     */
    @Transactional
    public List<DeviceIdentityCreationResult> generateDeviceIdentities(
            int count,
            String model,
            String batch,
            List<String> hardwareFingerprints
    ) {
        if (count <= 0 || count > 10000) {
            throw new IllegalArgumentException("Count must be between 1 and 10000");
        }

        if (hardwareFingerprints != null && hardwareFingerprints.size() != count) {
            throw new IllegalArgumentException("Hardware fingerprints list must match count");
        }

        List<DeviceIdentityCreationResult> results = new ArrayList<>(count);
        int year = LocalDateTime.now().getYear();

        for (int i = 0; i < count; i++) {
            // Generate unique device ID
            String deviceId = generateUniqueDeviceId(year);

            // Generate secure random secret
            String deviceSecret = generateDeviceSecret();

            // Hash the secret
            String secretHash = passwordEncoder.encode(deviceSecret);

            // Get hardware fingerprint if provided
            String fingerprint = hardwareFingerprints != null ? hardwareFingerprints.get(i) : null;

            // Create entity
            OrbMeshDeviceIdentity identity = OrbMeshDeviceIdentity.builder()
                    .deviceId(deviceId)
                    .deviceSecretHash(secretHash)
                    .hardwareFingerprint(fingerprint)
                    .deviceModel(model)
                    .manufacturingBatch(batch)
                    .status(DeviceProvisioningStatus.PENDING)
                    .build();

            identity = deviceIdentityRepository.save(identity);

            results.add(new DeviceIdentityCreationResult(deviceId, deviceSecret, identity));

            log.info("Created device identity: {} (batch: {}, model: {})", deviceId, batch, model);
        }

        log.info("Generated {} device identities for batch: {}", count, batch);
        return results;
    }

    /**
     * Register (activate) a device.
     * Called by the device on first boot with its credentials.
     *
     * @param deviceId Device identifier
     * @param deviceSecret Plain text device secret
     * @param publicIp Device's public IP address
     * @param hardwareFingerprint Hardware fingerprint (optional)
     * @param deviceName Friendly name for the device/server
     * @return Registration result with server credentials if successful
     */
    @Transactional
    public DeviceRegistrationResult registerDevice(
            String deviceId,
            String deviceSecret,
            String publicIp,
            String hardwareFingerprint,
            String deviceName
    ) {
        log.info("Device registration attempt: {} from IP: {}", deviceId, publicIp);

        // 1. Find device identity
        Optional<OrbMeshDeviceIdentity> optIdentity = deviceIdentityRepository.findByDeviceId(deviceId);
        if (optIdentity.isEmpty()) {
            log.warn("Registration failed: Unknown device ID: {}", deviceId);
            return new DeviceRegistrationResult(false, "Unknown device", null, null, null);
        }

        OrbMeshDeviceIdentity identity = optIdentity.get();

        // 2. Check rate limiting
        if (identity.isRateLimited()) {
            log.warn("Registration rate limited for device: {}", deviceId);
            return new DeviceRegistrationResult(false, "Too many attempts. Please wait.", null, null, null);
        }

        // 3. Verify device can be registered
        if (!identity.canRegister()) {
            String status = identity.getStatus().name();
            log.warn("Registration failed: Device {} has status: {}", deviceId, status);
            if (identity.getStatus() == DeviceProvisioningStatus.ACTIVATED) {
                return new DeviceRegistrationResult(false, "Device already activated", null, null, null);
            } else if (identity.getStatus() == DeviceProvisioningStatus.REVOKED) {
                return new DeviceRegistrationResult(false, "Device has been revoked", null, null, null);
            }
            return new DeviceRegistrationResult(false, "Device cannot be registered", null, null, null);
        }

        // 4. Verify device secret
        if (!passwordEncoder.matches(deviceSecret, identity.getDeviceSecretHash())) {
            identity.recordFailedAttempt();
            deviceIdentityRepository.save(identity);
            log.warn("Registration failed: Invalid secret for device: {} (attempt {})",
                    deviceId, identity.getFailedAttempts());
            return new DeviceRegistrationResult(false, "Invalid credentials", null, null, null);
        }

        // 5. Verify hardware fingerprint if required
        if (identity.getHardwareFingerprint() != null && !identity.getHardwareFingerprint().isEmpty()) {
            if (hardwareFingerprint == null || !identity.getHardwareFingerprint().equals(hardwareFingerprint)) {
                identity.recordFailedAttempt();
                deviceIdentityRepository.save(identity);
                log.warn("Registration failed: Hardware fingerprint mismatch for device: {}", deviceId);
                return new DeviceRegistrationResult(false, "Hardware verification failed", null, null, null);
            }
        }

        // 6. Detect region/country from IP (simplified - in production use GeoIP)
        String region = detectRegion(publicIp);
        String country = detectCountry(publicIp);

        // 7. Create OrbMeshServer entry
        String serverName = deviceName != null ? deviceName : "Device-" + deviceId;
        OrbMeshServer server = serverService.createServerForDevice(
                serverName,
                publicIp,
                region,
                country,
                identity.getDeviceModel()
        );

        // 8. Activate the device
        identity.activate(publicIp, hardwareFingerprint, region, country);
        identity.setServer(server);
        deviceIdentityRepository.save(identity);

        log.info("Device {} successfully registered as server {} (region: {})",
                deviceId, server.getId(), region);

        // 9. Return credentials (only time they're visible)
        return new DeviceRegistrationResult(
                true,
                "Device registered successfully",
                server,
                server.getApiKey(),
                server.getJwtSecret()
        );
    }

    /**
     * Revoke a device (admin action).
     * Prevents the device from operating and marks it as compromised.
     */
    @Transactional
    public boolean revokeDevice(String deviceId, String reason, String adminEmail) {
        Optional<OrbMeshDeviceIdentity> optIdentity = deviceIdentityRepository.findByDeviceId(deviceId);
        if (optIdentity.isEmpty()) {
            return false;
        }

        OrbMeshDeviceIdentity identity = optIdentity.get();
        identity.revoke(reason, adminEmail);

        // Also disable the associated server if exists
        if (identity.getServer() != null) {
            OrbMeshServer server = identity.getServer();
            server.setEnabled(false);
            server.setOnline(false);
            serverRepository.save(server);
        }

        deviceIdentityRepository.save(identity);
        log.info("Device {} revoked by {} (reason: {})", deviceId, adminEmail, reason);
        return true;
    }

    /**
     * Update device last seen timestamp (called during heartbeat).
     */
    @Transactional
    public void updateLastSeen(String deviceId) {
        deviceIdentityRepository.findByDeviceId(deviceId).ifPresent(identity -> {
            identity.setLastSeenAt(LocalDateTime.now());
            deviceIdentityRepository.save(identity);
        });
    }

    /**
     * Get device by ID.
     */
    public Optional<OrbMeshDeviceIdentity> getDevice(String deviceId) {
        return deviceIdentityRepository.findByDeviceId(deviceId);
    }

    /**
     * Get all devices by status.
     */
    public List<OrbMeshDeviceIdentity> getDevicesByStatus(DeviceProvisioningStatus status) {
        return deviceIdentityRepository.findByStatus(status);
    }

    /**
     * Get devices by manufacturing batch.
     */
    public List<OrbMeshDeviceIdentity> getDevicesByBatch(String batch) {
        return deviceIdentityRepository.findByManufacturingBatch(batch);
    }

    // ========== Helper Methods ==========

    /**
     * Generate a unique device ID.
     * Format: OM-{YEAR}-{6_CHAR_CODE}
     */
    private String generateUniqueDeviceId(int year) {
        String deviceId;
        int attempts = 0;
        do {
            String code = generateRandomCode(6);
            deviceId = String.format("%s-%d-%s", DEVICE_ID_PREFIX, year, code);
            attempts++;
            if (attempts > 100) {
                throw new RuntimeException("Failed to generate unique device ID after 100 attempts");
            }
        } while (deviceIdentityRepository.existsByDeviceId(deviceId));
        return deviceId;
    }

    /**
     * Generate a random alphanumeric code.
     */
    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Avoid ambiguous chars (0,O,1,I)
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a secure random device secret.
     */
    private String generateDeviceSecret() {
        byte[] secretBytes = new byte[DEVICE_SECRET_LENGTH];
        secureRandom.nextBytes(secretBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
    }

    /**
     * Detect region from IP address.
     * In production, use MaxMind GeoIP or similar.
     */
    private String detectRegion(String ip) {
        // Simplified implementation - in production use GeoIP database
        // For now, return a default region
        return "auto-detected";
    }

    /**
     * Detect country from IP address.
     * In production, use MaxMind GeoIP or similar.
     */
    private String detectCountry(String ip) {
        // Simplified implementation - in production use GeoIP database
        return "XX";
    }
}
