package com.orbvpn.api.service.hybrid;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.OrbMeshHomeDevice;
import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.enums.DeploymentType;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.OrbMeshHomeDeviceRepository;
import com.orbvpn.api.repository.OrbMeshNodeRepository;
import com.orbvpn.api.repository.OrbMeshPartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OrbMeshNodeService {

    private final OrbMeshNodeRepository nodeRepository;
    private final OrbMeshPartnerRepository partnerRepository;
    private final OrbMeshHomeDeviceRepository homeDeviceRepository;
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final int HEARTBEAT_TIMEOUT_MINUTES = 5;

    /**
     * Register a new node
     */
    public OrbMeshNode registerNode(String nodeUuid, DeploymentType deploymentType,
                                    Long partnerId, String nodeName, String region) {
        // Check if node exists
        var existingNode = nodeRepository.findByNodeUuid(nodeUuid);
        if (existingNode.isPresent()) {
            return existingNode.get();
        }

        OrbMeshNode.OrbMeshNodeBuilder builder = OrbMeshNode.builder()
                .nodeUuid(nodeUuid)
                .deploymentType(deploymentType)
                .region(region)
                .online(false);

        if (partnerId != null && deploymentType == DeploymentType.PARTNER_DC) {
            OrbMeshPartner partner = partnerRepository.findById(partnerId)
                    .orElseThrow(() -> new NotFoundException("Partner not found"));
            builder.partner(partner);
        }

        OrbMeshNode node = builder.build();
        nodeRepository.save(node);

        log.info("Registered new node: {} (type: {})", nodeUuid, deploymentType);
        return node;
    }

    /**
     * Process heartbeat from node
     */
    public OrbMeshNode processHeartbeat(String nodeUuid, boolean online, Integer currentConnections,
                                        Float cpuUsage, Float memoryUsage,
                                        Boolean wireguardHealthy, Boolean vlessHealthy,
                                        Boolean openconnectHealthy,
                                        NodeCapabilitiesInput capabilities) {
        OrbMeshNode node = nodeRepository.findByNodeUuid(nodeUuid)
                .orElseThrow(() -> new NotFoundException("Node not found: " + nodeUuid));

        node.setOnline(online);
        node.setLastHeartbeat(LocalDateTime.now());

        if (currentConnections != null) node.setCurrentConnections(currentConnections);

        // Update capabilities if provided
        if (capabilities != null) {
            updateNodeCapabilities(node, capabilities);
        }

        nodeRepository.save(node);
        return node;
    }

    /**
     * Update node capabilities
     */
    public OrbMeshNode updateNodeCapabilities(String nodeUuid, NodeCapabilitiesInput input) {
        OrbMeshNode node = nodeRepository.findByNodeUuid(nodeUuid)
                .orElseThrow(() -> new NotFoundException("Node not found: " + nodeUuid));
        updateNodeCapabilities(node, input);
        nodeRepository.save(node);
        return node;
    }

    private void updateNodeCapabilities(OrbMeshNode node, NodeCapabilitiesInput input) {
        if (input.publicIp != null) node.setPublicIp(input.publicIp);
        if (input.hasStaticIp != null) node.setHasStaticIp(input.hasStaticIp);
        if (input.isBehindCgnat != null) node.setIsBehindCgnat(input.isBehindCgnat);
        if (input.uploadMbps != null) node.setUploadMbps(input.uploadMbps);
        if (input.downloadMbps != null) node.setDownloadMbps(input.downloadMbps);
        if (input.maxConnections != null) node.setMaxConnections(input.maxConnections);
        if (input.cpuCores != null) node.setCpuCores(input.cpuCores);
        if (input.ramMb != null) node.setRamMb(input.ramMb);
        if (input.deviceType != null) node.setDeviceType(input.deviceType);
        if (input.supportsPortForward != null) node.setSupportsPortForward(input.supportsPortForward);
        if (input.supportsBridgeNode != null) node.setSupportsBridgeNode(input.supportsBridgeNode);
        if (input.supportsAi != null) node.setSupportsAi(input.supportsAi);
        if (input.canEarnTokens != null) node.setCanEarnTokens(input.canEarnTokens);
    }

    /**
     * Generate setup code for home device
     */
    public String generateHomeDeviceSetupCode(User user) {
        // Generate 8-character alphanumeric code
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }

        // Create placeholder node
        OrbMeshNode node = OrbMeshNode.builder()
                .nodeUuid(UUID.randomUUID().toString())
                .deploymentType(DeploymentType.HOME)
                .ownerUser(user)
                .online(false)
                .build();
        nodeRepository.save(node);

        // Create home device entry
        OrbMeshHomeDevice homeDevice = OrbMeshHomeDevice.builder()
                .node(node)
                .user(user)
                .setupCode(code.toString())
                .build();
        homeDeviceRepository.save(homeDevice);

        log.info("Generated home device setup code for user: {}", user.getId());
        return code.toString();
    }

    /**
     * Complete home device setup
     */
    public OrbMeshHomeDevice setupHomeDevice(String setupCode, String nodeName,
                                              String ddnsProvider, String ddnsHostname) {
        OrbMeshHomeDevice homeDevice = homeDeviceRepository.findBySetupCode(setupCode)
                .orElseThrow(() -> new NotFoundException("Invalid setup code"));

        if (homeDevice.getSetupCompletedAt() != null) {
            throw new IllegalStateException("Device already set up");
        }

        OrbMeshNode node = homeDevice.getNode();

        homeDevice.setSetupCompletedAt(LocalDateTime.now());
        homeDevice.setSetupCode(null);

        if (ddnsProvider != null) {
            homeDevice.setDdnsEnabled(true);
            homeDevice.setDdnsProvider(ddnsProvider);
            homeDevice.setDdnsHostname(ddnsHostname);
        }

        nodeRepository.save(node);
        homeDeviceRepository.save(homeDevice);

        log.info("Completed home device setup: {}", node.getNodeUuid());
        return homeDevice;
    }

    /**
     * Update home device settings
     */
    public OrbMeshHomeDevice updateHomeDevice(Long nodeId, String nodeName, Boolean ddnsEnabled,
                                               String ddnsProvider, String ddnsHostname,
                                               Boolean isPublic, Integer maxGuestConnections) {
        OrbMeshNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NotFoundException("Node not found"));

        OrbMeshHomeDevice homeDevice = homeDeviceRepository.findByNode(node)
                .orElseThrow(() -> new NotFoundException("Home device not found"));

        if (ddnsEnabled != null) homeDevice.setDdnsEnabled(ddnsEnabled);
        if (ddnsProvider != null) homeDevice.setDdnsProvider(ddnsProvider);
        if (ddnsHostname != null) homeDevice.setDdnsHostname(ddnsHostname);
        if (isPublic != null) homeDevice.setIsPublic(isPublic);
        if (maxGuestConnections != null) homeDevice.setMaxGuestConnections(maxGuestConnections);

        nodeRepository.save(node);
        homeDeviceRepository.save(homeDevice);

        return homeDevice;
    }

    /**
     * Remove home device
     */
    public void removeHomeDevice(Long nodeId) {
        OrbMeshNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NotFoundException("Node not found"));

        homeDeviceRepository.findByNode(node).ifPresent(homeDeviceRepository::delete);
        nodeRepository.delete(node);

        log.info("Removed home device: {}", node.getNodeUuid());
    }

    /**
     * Mark stale nodes as offline (scheduled task)
     */
    @Scheduled(fixedRate = 60000)
    public void markStaleNodesOffline() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(HEARTBEAT_TIMEOUT_MINUTES);
        int count = nodeRepository.markOfflineByHeartbeat(cutoff);
        if (count > 0) {
            log.info("Marked {} stale nodes as offline", count);
        }
    }

    /**
     * Update node bandwidth statistics
     */
    public void updateBandwidthStats(String nodeUuid, BigDecimal bandwidthGb) {
        OrbMeshNode node = nodeRepository.findByNodeUuid(nodeUuid)
                .orElseThrow(() -> new NotFoundException("Node not found: " + nodeUuid));

        node.setTotalBandwidthServedGb(
                node.getTotalBandwidthServedGb().add(bandwidthGb));

        nodeRepository.save(node);
    }

    // Query methods
    public OrbMeshNode getById(Long id) {
        return nodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Node not found"));
    }

    public OrbMeshNode getByUuid(String uuid) {
        return nodeRepository.findByNodeUuid(uuid)
                .orElseThrow(() -> new NotFoundException("Node not found"));
    }

    public List<OrbMeshNode> getOnlineNodes() {
        return nodeRepository.findAvailableByRegion(null, PageRequest.of(0, 1000));
    }

    public List<OrbMeshNode> getOnlineNodesByDeploymentType(DeploymentType type) {
        return nodeRepository.findActiveByDeploymentType(type);
    }

    public List<OrbMeshNode> getOnlineNodesByRegion(String region) {
        return nodeRepository.findByRegionAndIsActiveTrue(region);
    }

    public List<OrbMeshNode> getNodesByPartner(OrbMeshPartner partner) {
        return nodeRepository.findByPartner(partner);
    }

    public List<OrbMeshHomeDevice> getHomeDevicesByUser(User user) {
        return homeDeviceRepository.findByUser(user);
    }

    public Page<OrbMeshNode> getAllNodes(Pageable pageable) {
        return nodeRepository.findAll(pageable);
    }

    public Page<OrbMeshNode> getNodesByDeploymentType(DeploymentType type, Pageable pageable) {
        return nodeRepository.findAll(pageable); // Simplified - add filtered query if needed
    }

    public Page<OrbMeshNode> getNodesByOnlineStatus(boolean online, Pageable pageable) {
        return nodeRepository.findAll(pageable); // Simplified - add filtered query if needed
    }

    // Statistics
    public int countOnlineNodes() {
        return (int) nodeRepository.countByIsActiveTrueAndOnlineTrue();
    }

    public int countByDeploymentType(DeploymentType type) {
        return nodeRepository.findByDeploymentType(type).size();
    }

    public List<Object[]> getRegionStatistics() {
        return nodeRepository.countOnlineByRegion();
    }

    public List<Object[]> getDeploymentTypeStatistics() {
        return nodeRepository.countByDeploymentType();
    }

    // Capability queries
    public List<OrbMeshNode> getNodesWithStaticIpSupport() {
        return nodeRepository.findWithStaticIpCapability();
    }

    public List<OrbMeshNode> getNodesWithPortForwardCapability() {
        return nodeRepository.findWithPortForwardCapability();
    }

    // Input record for capabilities
    public record NodeCapabilitiesInput(
            String publicIp,
            Boolean hasStaticIp,
            Boolean isBehindCgnat,
            Integer uploadMbps,
            Integer downloadMbps,
            Integer maxConnections,
            Integer cpuCores,
            Integer ramMb,
            String deviceType,
            Boolean supportsPortForward,
            Boolean supportsBridgeNode,
            Boolean supportsAi,
            Boolean canEarnTokens
    ) {}
}
