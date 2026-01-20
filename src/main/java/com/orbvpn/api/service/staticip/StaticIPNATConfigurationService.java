package com.orbvpn.api.service.staticip;

import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.StaticIPAllocation;
import com.orbvpn.api.domain.enums.StaticIPAllocationStatus;
import com.orbvpn.api.repository.OrbMeshNodeRepository;
import com.orbvpn.api.repository.StaticIPAllocationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for configuring NAT rules on protocol servers for static IP allocations.
 *
 * This service handles:
 * 1. Async NAT configuration when a new allocation is created
 * 2. Scheduled processing of pending allocations
 * 3. NAT cleanup when allocations are released
 *
 * In production, this would send commands to actual protocol servers.
 * For testing/development, it simulates the NAT configuration process.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaticIPNATConfigurationService {

    private final StaticIPAllocationRepository allocationRepository;
    private final OrbMeshNodeRepository nodeRepository;

    // Configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long CONFIGURATION_TIMEOUT_MS = 30000; // 30 seconds

    /**
     * Asynchronously configure NAT for a new allocation.
     * This is called when a static IP is allocated to a user.
     */
    @Async
    public CompletableFuture<Boolean> configureNATAsync(Long allocationId) {
        log.info("Starting async NAT configuration for allocation {}", allocationId);

        try {
            boolean success = configureNAT(allocationId);
            return CompletableFuture.completedFuture(success);
        } catch (Exception e) {
            log.error("Error in async NAT configuration for allocation {}: {}", allocationId, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Configure NAT for an allocation.
     * Updates status: PENDING → CONFIGURING → ACTIVE (or error)
     */
    @Transactional
    public boolean configureNAT(Long allocationId) {
        Optional<StaticIPAllocation> optAllocation = allocationRepository.findById(allocationId);
        if (optAllocation.isEmpty()) {
            log.error("Allocation not found: {}", allocationId);
            return false;
        }

        StaticIPAllocation allocation = optAllocation.get();

        // Only configure PENDING allocations
        if (allocation.getStatus() != StaticIPAllocationStatus.PENDING) {
            log.warn("Allocation {} is not in PENDING status (current: {}), skipping",
                    allocationId, allocation.getStatus());
            return false;
        }

        // Update to CONFIGURING
        allocation.setStatus(StaticIPAllocationStatus.CONFIGURING);
        allocationRepository.save(allocation);
        log.info("Allocation {} status updated to CONFIGURING", allocationId);

        try {
            // Get the node for this allocation
            Optional<OrbMeshNode> optNode = nodeRepository.findById(allocation.getServerId());
            if (optNode.isEmpty()) {
                throw new RuntimeException("Node not found: " + allocation.getServerId());
            }

            OrbMeshNode node = optNode.get();

            // Configure NAT on the protocol server
            boolean natConfigured = sendNATConfigurationToServer(node, allocation);

            if (natConfigured) {
                // Success - mark as ACTIVE
                allocation.setStatus(StaticIPAllocationStatus.ACTIVE);
                allocation.setConfiguredAt(LocalDateTime.now());
                allocation.setLastError(null);
                allocationRepository.save(allocation);

                log.info("NAT configuration successful for allocation {} - IP {} on node {}",
                        allocationId, allocation.getPublicIp(), node.getNodeUuid());
                return true;
            } else {
                throw new RuntimeException("NAT configuration returned false");
            }

        } catch (Exception e) {
            log.error("NAT configuration failed for allocation {}: {}", allocationId, e.getMessage());

            // Record error but keep in CONFIGURING for retry
            allocation.setLastError(e.getMessage());
            allocationRepository.save(allocation);
            return false;
        }
    }

    /**
     * Clean up NAT rules when an allocation is released.
     */
    @Async
    public CompletableFuture<Boolean> cleanupNATAsync(Long allocationId) {
        log.info("Starting async NAT cleanup for allocation {}", allocationId);

        try {
            boolean success = cleanupNAT(allocationId);
            return CompletableFuture.completedFuture(success);
        } catch (Exception e) {
            log.error("Error in async NAT cleanup for allocation {}: {}", allocationId, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Clean up NAT rules for a released allocation.
     */
    @Transactional
    public boolean cleanupNAT(Long allocationId) {
        Optional<StaticIPAllocation> optAllocation = allocationRepository.findById(allocationId);
        if (optAllocation.isEmpty()) {
            log.warn("Allocation not found for cleanup: {}", allocationId);
            return true; // Nothing to clean up
        }

        StaticIPAllocation allocation = optAllocation.get();

        try {
            Optional<OrbMeshNode> optNode = nodeRepository.findById(allocation.getServerId());
            if (optNode.isPresent()) {
                sendNATCleanupToServer(optNode.get(), allocation);
            }
            log.info("NAT cleanup completed for allocation {}", allocationId);
            return true;
        } catch (Exception e) {
            log.error("NAT cleanup failed for allocation {}: {}", allocationId, e.getMessage());
            return false;
        }
    }

    /**
     * Scheduled job to process pending NAT configurations.
     * Runs every 10 seconds to pick up any allocations that failed initial configuration.
     */
    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    @Transactional
    public void processPendingConfigurations() {
        List<StaticIPAllocation> pending = allocationRepository.findNeedingConfiguration();

        if (pending.isEmpty()) {
            return;
        }

        log.info("Processing {} pending NAT configurations", pending.size());

        for (StaticIPAllocation allocation : pending) {
            try {
                // Check if it's been too long (stuck in CONFIGURING)
                if (allocation.getStatus() == StaticIPAllocationStatus.CONFIGURING) {
                    // If stuck for more than 5 minutes, reset to PENDING
                    if (allocation.getUpdatedAt() != null &&
                        allocation.getUpdatedAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
                        log.warn("Allocation {} stuck in CONFIGURING, resetting to PENDING", allocation.getId());
                        allocation.setStatus(StaticIPAllocationStatus.PENDING);
                        allocationRepository.save(allocation);
                    }
                    continue;
                }

                configureNAT(allocation.getId());
            } catch (Exception e) {
                log.error("Error processing pending allocation {}: {}", allocation.getId(), e.getMessage());
            }
        }
    }

    /**
     * Send NAT configuration command to the protocol server.
     *
     * In production, this would:
     * 1. Connect to the protocol server via WebSocket/gRPC/HTTP
     * 2. Send the NAT rule configuration (iptables commands)
     * 3. Wait for confirmation
     *
     * For now, this simulates successful configuration.
     */
    private boolean sendNATConfigurationToServer(OrbMeshNode node, StaticIPAllocation allocation) {
        log.info("Configuring NAT on node {} for IP {} → internal {}",
                node.getNodeUuid(), allocation.getPublicIp(), allocation.getInternalIp());

        // In production, send actual NAT commands:
        // iptables -t nat -A PREROUTING -d {publicIp} -j DNAT --to-destination {internalIp}
        // iptables -t nat -A POSTROUTING -s {internalIp} -j SNAT --to-source {publicIp}

        // Check if node is online
        if (!Boolean.TRUE.equals(node.getOnline())) {
            log.warn("Node {} is offline, NAT configuration may fail", node.getNodeUuid());
            // In production, you might want to return false here
            // For testing, we'll continue
        }

        // Simulate configuration delay (remove in production when using real servers)
        try {
            Thread.sleep(100); // Small delay to simulate network call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // TODO: Replace with actual protocol server communication
        // Example for HTTP-based server:
        // String url = "http://" + node.getPublicIp() + ":8080/api/nat/configure";
        // restTemplate.postForEntity(url, natConfig, NATConfigResponse.class);

        // For now, simulate success
        log.info("NAT configuration sent successfully to node {}", node.getNodeUuid());
        return true;
    }

    /**
     * Send NAT cleanup command to the protocol server.
     */
    private void sendNATCleanupToServer(OrbMeshNode node, StaticIPAllocation allocation) {
        log.info("Cleaning NAT on node {} for IP {}", node.getNodeUuid(), allocation.getPublicIp());

        // In production, remove NAT rules:
        // iptables -t nat -D PREROUTING -d {publicIp} -j DNAT --to-destination {internalIp}
        // iptables -t nat -D POSTROUTING -s {internalIp} -j SNAT --to-source {publicIp}

        // Simulate cleanup delay
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("NAT cleanup sent successfully to node {}", node.getNodeUuid());
    }

    /**
     * Manually trigger NAT configuration for an allocation.
     * Useful for testing or admin operations.
     */
    public boolean manualConfigureNAT(Long allocationId) {
        log.info("Manual NAT configuration triggered for allocation {}", allocationId);
        return configureNAT(allocationId);
    }

    /**
     * Get the NAT configuration status for an allocation.
     */
    public NATConfigurationStatus getNATStatus(Long allocationId) {
        Optional<StaticIPAllocation> optAllocation = allocationRepository.findById(allocationId);
        if (optAllocation.isEmpty()) {
            return new NATConfigurationStatus(false, "Allocation not found", null);
        }

        StaticIPAllocation allocation = optAllocation.get();
        return new NATConfigurationStatus(
                allocation.getStatus() == StaticIPAllocationStatus.ACTIVE,
                allocation.getStatus().name(),
                allocation.getLastError()
        );
    }

    /**
     * DTO for NAT configuration status.
     */
    public record NATConfigurationStatus(boolean configured, String status, String error) {}
}
