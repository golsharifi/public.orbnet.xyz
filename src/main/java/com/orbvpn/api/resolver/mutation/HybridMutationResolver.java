package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbMeshHomeDevice;
import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.enums.DeploymentType;
import com.orbvpn.api.domain.enums.PartnerTier;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.hybrid.OrbMeshNodeService;
import com.orbvpn.api.service.hybrid.OrbMeshNodeService.NodeCapabilitiesInput;
import com.orbvpn.api.service.hybrid.OrbMeshPartnerService;
import com.orbvpn.api.repository.OrbMeshPartnerRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HybridMutationResolver {

    private final OrbMeshPartnerService partnerService;
    private final OrbMeshNodeService nodeService;
    private final OrbMeshPartnerRepository partnerRepository;
    private final UserService userService;

    // ========== Partner Mutations ==========

    @MutationMapping
    public PartnerResponse registerPartner(@Argument Map<String, Object> input) {
        try {
            String partnerName = (String) input.get("partnerName");
            String contactEmail = (String) input.get("contactEmail");
            String contactPhone = (String) input.get("contactPhone");
            String companyName = (String) input.get("companyName");

            OrbMeshPartner partner = partnerService.registerPartner(
                    partnerName, contactEmail, contactPhone, companyName);

            String apiKey = partnerService.generateApiKey(partner.getId());

            log.info("Registered new partner: {}", partner.getPartnerUuid());
            return new PartnerResponse(true, "Partner registered successfully", partner, apiKey);
        } catch (Exception e) {
            log.error("Error registering partner: {}", e.getMessage(), e);
            return new PartnerResponse(false, e.getMessage(), null, null);
        }
    }

    @MutationMapping
    public PartnerResponse updatePartner(@Argument Map<String, Object> input) {
        try {
            User user = userService.getUser();
            OrbMeshPartner partner = partnerRepository.findByContactEmail(user.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("No partner found for current user"));

            String partnerName = (String) input.get("partnerName");
            String contactEmail = (String) input.get("contactEmail");
            String contactPhone = (String) input.get("contactPhone");
            String companyName = (String) input.get("companyName");

            partner = partnerService.updatePartner(partner.getId(),
                    partnerName, contactEmail, contactPhone, companyName);

            return new PartnerResponse(true, "Partner updated successfully", partner, null);
        } catch (Exception e) {
            log.error("Error updating partner: {}", e.getMessage(), e);
            return new PartnerResponse(false, e.getMessage(), null, null);
        }
    }

    @MutationMapping
    public PartnerResponse regeneratePartnerApiKey() {
        try {
            User user = userService.getUser();
            OrbMeshPartner partner = partnerRepository.findByContactEmail(user.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("No partner found for current user"));

            String apiKey = partnerService.generateApiKey(partner.getId());

            return new PartnerResponse(true, "API key regenerated successfully", partner, apiKey);
        } catch (Exception e) {
            log.error("Error regenerating API key: {}", e.getMessage(), e);
            return new PartnerResponse(false, e.getMessage(), null, null);
        }
    }

    // ========== Node Mutations (called by OrbMesh server) ==========

    @Unsecured
    @MutationMapping
    public NodeResponse registerNode(@Argument Map<String, Object> input) {
        try {
            String nodeUuid = (String) input.get("nodeUuid");
            String deploymentTypeStr = (String) input.get("deploymentType");
            Long partnerId = input.get("partnerId") != null ?
                    Long.valueOf(input.get("partnerId").toString()) : null;
            String nodeName = (String) input.get("nodeName");
            String region = (String) input.get("region");

            DeploymentType deploymentType = DeploymentType.valueOf(deploymentTypeStr.toUpperCase());

            OrbMeshNode node = nodeService.registerNode(
                    nodeUuid, deploymentType, partnerId, nodeName, region);

            log.info("Registered node: {}", nodeUuid);
            return new NodeResponse(true, "Node registered successfully", node);
        } catch (Exception e) {
            log.error("Error registering node: {}", e.getMessage(), e);
            return new NodeResponse(false, e.getMessage(), null);
        }
    }

    @Unsecured
    @MutationMapping
    public NodeResponse updateNodeCapabilities(@Argument Map<String, Object> input) {
        try {
            String nodeUuid = (String) input.get("nodeUuid");

            NodeCapabilitiesInput capabilities = buildCapabilitiesInput(input);
            OrbMeshNode node = nodeService.updateNodeCapabilities(nodeUuid, capabilities);

            return new NodeResponse(true, "Node capabilities updated", node);
        } catch (Exception e) {
            log.error("Error updating node capabilities: {}", e.getMessage(), e);
            return new NodeResponse(false, e.getMessage(), null);
        }
    }

    @Unsecured
    @MutationMapping
    public HeartbeatResponse nodeHeartbeat(@Argument Map<String, Object> input) {
        try {
            String nodeUuid = (String) input.get("nodeUuid");
            Boolean online = (Boolean) input.get("online");
            Integer currentConnections = input.get("currentConnections") != null ?
                    ((Number) input.get("currentConnections")).intValue() : null;
            Float cpuUsage = input.get("cpuUsage") != null ?
                    ((Number) input.get("cpuUsage")).floatValue() : null;
            Float memoryUsage = input.get("memoryUsage") != null ?
                    ((Number) input.get("memoryUsage")).floatValue() : null;
            Boolean wireguardHealthy = (Boolean) input.get("wireguardHealthy");
            Boolean vlessHealthy = (Boolean) input.get("vlessHealthy");
            Boolean openconnectHealthy = (Boolean) input.get("openconnectHealthy");

            @SuppressWarnings("unchecked")
            Map<String, Object> capsMap = (Map<String, Object>) input.get("capabilities");
            NodeCapabilitiesInput capabilities = capsMap != null ? buildCapabilitiesInput(capsMap) : null;

            OrbMeshNode node = nodeService.processHeartbeat(
                    nodeUuid, online != null && online,
                    currentConnections, cpuUsage, memoryUsage,
                    wireguardHealthy, vlessHealthy, openconnectHealthy,
                    capabilities);

            return new HeartbeatResponse(true, "Heartbeat recorded", node.getId(), LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error processing heartbeat: {}", e.getMessage(), e);
            return new HeartbeatResponse(false, e.getMessage(), null, LocalDateTime.now());
        }
    }

    // ========== Home Device Mutations ==========

    @MutationMapping
    public HomeDeviceResponse generateHomeDeviceSetupCode() {
        try {
            User user = userService.getUser();
            String setupCode = nodeService.generateHomeDeviceSetupCode(user);

            return new HomeDeviceResponse(true, "Setup code generated", null, setupCode);
        } catch (Exception e) {
            log.error("Error generating setup code: {}", e.getMessage(), e);
            return new HomeDeviceResponse(false, e.getMessage(), null, null);
        }
    }

    @MutationMapping
    public HomeDeviceResponse setupHomeDevice(@Argument Map<String, Object> input) {
        try {
            String setupCode = (String) input.get("setupCode");
            String nodeName = (String) input.get("nodeName");
            String ddnsProvider = (String) input.get("ddnsProvider");
            String ddnsHostname = (String) input.get("ddnsHostname");

            OrbMeshHomeDevice device = nodeService.setupHomeDevice(
                    setupCode, nodeName, ddnsProvider, ddnsHostname);

            return new HomeDeviceResponse(true, "Device setup completed", device, null);
        } catch (Exception e) {
            log.error("Error setting up home device: {}", e.getMessage(), e);
            return new HomeDeviceResponse(false, e.getMessage(), null, null);
        }
    }

    @MutationMapping
    public HomeDeviceResponse updateHomeDevice(@Argument Map<String, Object> input) {
        try {
            Long nodeId = Long.valueOf(input.get("nodeId").toString());
            String nodeName = (String) input.get("nodeName");
            Boolean ddnsEnabled = (Boolean) input.get("ddnsEnabled");
            String ddnsProvider = (String) input.get("ddnsProvider");
            String ddnsHostname = (String) input.get("ddnsHostname");
            Boolean isPublic = (Boolean) input.get("isPublic");
            Integer maxGuestConnections = input.get("maxGuestConnections") != null ?
                    ((Number) input.get("maxGuestConnections")).intValue() : null;

            OrbMeshHomeDevice device = nodeService.updateHomeDevice(
                    nodeId, nodeName, ddnsEnabled, ddnsProvider, ddnsHostname,
                    isPublic, maxGuestConnections);

            return new HomeDeviceResponse(true, "Device updated", device, null);
        } catch (Exception e) {
            log.error("Error updating home device: {}", e.getMessage(), e);
            return new HomeDeviceResponse(false, e.getMessage(), null, null);
        }
    }

    @MutationMapping
    public NodeResponse removeHomeDevice(@Argument Long nodeId) {
        try {
            nodeService.removeHomeDevice(nodeId);
            return new NodeResponse(true, "Home device removed", null);
        } catch (Exception e) {
            log.error("Error removing home device: {}", e.getMessage(), e);
            return new NodeResponse(false, e.getMessage(), null);
        }
    }

    // ========== Admin Mutations ==========

    @Secured(ADMIN)
    @MutationMapping
    public PartnerResponse adminUpdatePartnerTier(
            @Argument Long partnerId,
            @Argument String tier) {
        try {
            PartnerTier partnerTier = PartnerTier.valueOf(tier.toUpperCase());
            OrbMeshPartner partner = partnerService.updateTier(partnerId, partnerTier);
            return new PartnerResponse(true, "Partner tier updated", partner, null);
        } catch (Exception e) {
            log.error("Error updating partner tier: {}", e.getMessage(), e);
            return new PartnerResponse(false, e.getMessage(), null, null);
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public PartnerResponse adminVerifyPartner(
            @Argument Long partnerId,
            @Argument Boolean verified) {
        try {
            OrbMeshPartner partner = partnerService.verifyPartner(partnerId, verified, null);
            return new PartnerResponse(true,
                    verified ? "Partner verified" : "Partner verification removed", partner, null);
        } catch (Exception e) {
            log.error("Error verifying partner: {}", e.getMessage(), e);
            return new PartnerResponse(false, e.getMessage(), null, null);
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public PartnerResponse adminDeactivatePartner(@Argument Long partnerId) {
        try {
            OrbMeshPartner partner = partnerService.deactivatePartner(partnerId);
            return new PartnerResponse(true, "Partner deactivated", partner, null);
        } catch (Exception e) {
            log.error("Error deactivating partner: {}", e.getMessage(), e);
            return new PartnerResponse(false, e.getMessage(), null, null);
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public NodeResponse adminRemoveNode(@Argument Long nodeId) {
        try {
            nodeService.removeHomeDevice(nodeId);
            return new NodeResponse(true, "Node removed", null);
        } catch (Exception e) {
            log.error("Error removing node: {}", e.getMessage(), e);
            return new NodeResponse(false, e.getMessage(), null);
        }
    }

    // Helper method to build capabilities input
    private NodeCapabilitiesInput buildCapabilitiesInput(Map<String, Object> input) {
        return new NodeCapabilitiesInput(
                (String) input.get("publicIp"),
                (Boolean) input.get("hasStaticIp"),
                (Boolean) input.get("isBehindCgnat"),
                input.get("uploadMbps") != null ? ((Number) input.get("uploadMbps")).intValue() : null,
                input.get("downloadMbps") != null ? ((Number) input.get("downloadMbps")).intValue() : null,
                input.get("maxConnections") != null ? ((Number) input.get("maxConnections")).intValue() : null,
                input.get("cpuCores") != null ? ((Number) input.get("cpuCores")).intValue() : null,
                input.get("ramMb") != null ? ((Number) input.get("ramMb")).intValue() : null,
                (String) input.get("deviceType"),
                (Boolean) input.get("supportsPortForward"),
                (Boolean) input.get("supportsBridgeNode"),
                (Boolean) input.get("supportsAi"),
                (Boolean) input.get("canEarnTokens")
        );
    }

    // Response records
    public record PartnerResponse(boolean success, String message, OrbMeshPartner partner, String apiKey) {}
    public record NodeResponse(boolean success, String message, OrbMeshNode node) {}
    public record HomeDeviceResponse(boolean success, String message, OrbMeshHomeDevice homeDevice, String setupCode) {}
    public record HeartbeatResponse(boolean success, String message, Long nodeId, LocalDateTime serverTime) {}
}
