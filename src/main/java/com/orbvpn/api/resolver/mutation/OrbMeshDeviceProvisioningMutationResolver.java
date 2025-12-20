package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.OrbMeshDeviceBatchInput;
import com.orbvpn.api.domain.dto.OrbMeshDeviceBatchResult;
import com.orbvpn.api.domain.dto.OrbMeshDeviceCertificate;
import com.orbvpn.api.domain.dto.OrbMeshDeviceIdentityCreationResult;
import com.orbvpn.api.domain.dto.OrbMeshDeviceIdentityView;
import com.orbvpn.api.domain.dto.OrbMeshDeviceRegistrationInput;
import com.orbvpn.api.domain.dto.OrbMeshDeviceRegistrationResult;
import com.orbvpn.api.domain.dto.OrbMeshDeviceRevokeInput;
import com.orbvpn.api.domain.entity.OrbMeshDeviceIdentity;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.OrbMeshCertificateAuthorityService;
import com.orbvpn.api.service.OrbMeshDeviceProvisioningService;
import com.orbvpn.api.service.OrbMeshDeviceProvisioningService.DeviceIdentityCreationResult;
import com.orbvpn.api.service.OrbMeshDeviceProvisioningService.DeviceRegistrationResult;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * GraphQL Mutation Resolver for OrbMesh Device Provisioning.
 *
 * Handles:
 * - Device registration (unauthenticated - device provides credentials)
 * - Manufacturing operations (admin only)
 * - Device management (admin only)
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class OrbMeshDeviceProvisioningMutationResolver {

    private final OrbMeshDeviceProvisioningService provisioningService;
    private final OrbMeshCertificateAuthorityService certificateAuthorityService;
    private final UserRepository userRepository;

    /**
     * Register a device (called by device on first boot).
     * This endpoint is accessible without authentication - the device
     * authenticates using its device_id and device_secret.
     */
    @PermitAll
    @MutationMapping
    public OrbMeshDeviceRegistrationResult registerOrbMeshDevice(@Argument OrbMeshDeviceRegistrationInput input) {
        log.info("Device registration request received for: {}", input.getDeviceId());

        DeviceRegistrationResult result = provisioningService.registerDevice(
                input.getDeviceId(),
                input.getDeviceSecret(),
                input.getPublicIp(),
                input.getHardwareFingerprint(),
                input.getDeviceName()
        );

        // Convert to DTO
        OrbMeshDeviceRegistrationResult response = new OrbMeshDeviceRegistrationResult();
        response.setSuccess(result.success());
        response.setMessage(result.message());

        if (result.success() && result.server() != null) {
            response.setServerId(result.server().getId().toString());
            response.setServerName(result.server().getName());
            response.setApiKey(result.apiKey());
            response.setJwtSecret(result.jwtSecret());
            response.setRegion(result.server().getRegion());

            // Issue TLS certificate from OrbNet CA
            if (certificateAuthorityService.isCAReady()) {
                try {
                    var deviceCert = certificateAuthorityService.issueCertificate(
                            input.getDeviceId(),
                            input.getPublicIp(),
                            result.server().getHostname()
                    );

                    OrbMeshDeviceCertificate certDto = OrbMeshDeviceCertificate.builder()
                            .certificatePem(deviceCert.certificatePem())
                            .privateKeyPem(deviceCert.privateKeyPem())
                            .caCertificatePem(deviceCert.caCertificatePem())
                            .serialNumber(deviceCert.serialNumber())
                            .validFrom(deviceCert.validFrom())
                            .validUntil(deviceCert.validUntil())
                            .build();

                    response.setCertificate(certDto);
                    log.info("✅ Certificate issued for device: {}", input.getDeviceId());
                } catch (Exception e) {
                    log.error("Failed to issue certificate for device {}: {}", input.getDeviceId(), e.getMessage());
                    // Continue without certificate - device can still operate without TLS
                }
            } else {
                log.warn("CA not ready - device {} will not receive a certificate", input.getDeviceId());
            }

            // Provide standard DNS/NTP servers
            response.setDnsServers(Arrays.asList("8.8.8.8", "1.1.1.1"));
            response.setNtpServers(Arrays.asList("time.google.com", "pool.ntp.org"));
        }

        return response;
    }

    /**
     * Generate a batch of device identities for manufacturing.
     * Admin only.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public OrbMeshDeviceBatchResult generateOrbMeshDeviceBatch(@Argument OrbMeshDeviceBatchInput input) {
        log.info("Generating device batch: {} devices, model: {}, batch: {}",
                input.getCount(), input.getDeviceModel(), input.getManufacturingBatch());

        try {
            List<DeviceIdentityCreationResult> created = provisioningService.generateDeviceIdentities(
                    input.getCount(),
                    input.getDeviceModel(),
                    input.getManufacturingBatch(),
                    input.getHardwareFingerprints()
            );

            // Convert to DTOs
            List<OrbMeshDeviceIdentityCreationResult> devices = created.stream()
                    .map(this::toCreationResultDto)
                    .collect(Collectors.toList());

            OrbMeshDeviceBatchResult result = new OrbMeshDeviceBatchResult();
            result.setSuccess(true);
            result.setMessage("Successfully generated " + created.size() + " device identities");
            result.setCount(created.size());
            result.setBatchId(input.getManufacturingBatch());
            result.setDevices(devices);

            log.info("Successfully generated {} device identities for batch: {}",
                    created.size(), input.getManufacturingBatch());
            return result;

        } catch (Exception e) {
            log.error("Failed to generate device batch: {}", e.getMessage(), e);
            OrbMeshDeviceBatchResult result = new OrbMeshDeviceBatchResult();
            result.setSuccess(false);
            result.setMessage("Failed to generate devices: " + e.getMessage());
            result.setCount(0);
            result.setBatchId(input.getManufacturingBatch());
            result.setDevices(List.of());
            return result;
        }
    }

    /**
     * Revoke a device (admin only).
     * Marks the device as compromised and prevents it from operating.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public Boolean revokeOrbMeshDevice(@Argument OrbMeshDeviceRevokeInput input) {
        log.info("Revoking device: {} (reason: {})", input.getDeviceId(), input.getReason());
        // TODO: Get admin email from security context
        String adminEmail = "admin@orbnet.xyz";
        return provisioningService.revokeDevice(input.getDeviceId(), input.getReason(), adminEmail);
    }

    /**
     * Re-activate a deactivated device (admin only).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public OrbMeshDeviceIdentityView reactivateOrbMeshDevice(@Argument String deviceId) {
        log.info("Reactivating device: {}", deviceId);
        // TODO: Implement reactivation logic
        return provisioningService.getDevice(deviceId)
                .map(this::toDeviceView)
                .orElse(null);
    }

    /**
     * Transfer device ownership to a user (admin only).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @MutationMapping
    public OrbMeshDeviceIdentityView transferOrbMeshDeviceOwnership(@Argument String deviceId, @Argument Integer newOwnerId) {
        log.info("Transferring device {} to user {}", deviceId, newOwnerId);

        Optional<OrbMeshDeviceIdentity> optDevice = provisioningService.getDevice(deviceId);
        if (optDevice.isEmpty()) {
            return null;
        }

        Optional<User> optUser = userRepository.findById(newOwnerId);
        if (optUser.isEmpty()) {
            return null;
        }

        OrbMeshDeviceIdentity device = optDevice.get();
        device.setOwner(optUser.get());
        // Note: Need to save through repository - this is simplified
        return toDeviceView(device);
    }

    // ========== Helper Methods ==========

    private OrbMeshDeviceIdentityView toDeviceView(OrbMeshDeviceIdentity entity) {
        OrbMeshDeviceIdentityView view = new OrbMeshDeviceIdentityView();
        view.setId(entity.getId().toString());
        view.setDeviceId(entity.getDeviceId());
        view.setDeviceModel(entity.getDeviceModel());
        view.setManufacturingBatch(entity.getManufacturingBatch());
        view.setStatus(entity.getStatus().name());
        view.setActivationIp(entity.getActivationIp());
        view.setDetectedRegion(entity.getDetectedRegion());
        view.setDetectedCountry(entity.getDetectedCountry());
        view.setFailedAttempts(entity.getFailedAttempts() != null ? entity.getFailedAttempts() : 0);

        if (entity.getServer() != null) {
            view.setServerId(entity.getServer().getId().toString());
            view.setServerName(entity.getServer().getName());
        }

        if (entity.getOwner() != null) {
            view.setOwnerId(String.valueOf(entity.getOwner().getId()));
            view.setOwnerEmail(entity.getOwner().getEmail());
        }

        if (entity.getCreatedAt() != null) {
            view.setCreatedAt(entity.getCreatedAt().toString());
        }
        if (entity.getActivatedAt() != null) {
            view.setActivatedAt(entity.getActivatedAt().toString());
        }
        if (entity.getLastSeenAt() != null) {
            view.setLastSeenAt(entity.getLastSeenAt().toString());
        }
        if (entity.getRevokedAt() != null) {
            view.setRevokedAt(entity.getRevokedAt().toString());
        }
        view.setRevocationReason(entity.getRevocationReason());

        return view;
    }

    private OrbMeshDeviceIdentityCreationResult toCreationResultDto(DeviceIdentityCreationResult result) {
        OrbMeshDeviceIdentityCreationResult dto = new OrbMeshDeviceIdentityCreationResult();
        dto.setDeviceId(result.deviceId());
        dto.setDeviceSecret(result.deviceSecret());
        dto.setIdentity(toDeviceView(result.identity()));
        return dto;
    }
}
