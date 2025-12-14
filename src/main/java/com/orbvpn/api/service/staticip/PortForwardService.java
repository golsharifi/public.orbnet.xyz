package com.orbvpn.api.service.staticip;

import com.orbvpn.api.domain.dto.staticip.CreatePortForwardRequest;
import com.orbvpn.api.domain.dto.staticip.PortForwardRuleDTO;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortForwardService {

    private final PortForwardRuleRepository ruleRepository;
    private final PortForwardAddonRepository addonRepository;
    private final StaticIPAllocationRepository allocationRepository;

    // Reserved/blocked port ranges
    private static final Set<Integer> BLOCKED_PORTS = new HashSet<>(Set.of(
            22, 23, 25, 53, 80, 110, 143, 443, 465, 587, 993, 995,  // Well-known services
            1433, 1434, 3306, 3389, 5432, 5900, 6379, 27017  // Database/admin ports
    ));

    private static final int MIN_ALLOWED_PORT = 1024;
    private static final int MAX_ALLOWED_PORT = 65535;

    /**
     * Create a new port forward rule
     */
    @Transactional
    public PortForwardRule createPortForwardRule(User user, CreatePortForwardRequest request) {
        // Get allocation
        StaticIPAllocation allocation = allocationRepository.findById(request.getAllocationId())
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found"));

        // Verify ownership
        if (allocation.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Allocation does not belong to user");
        }

        // Verify allocation is active
        if (allocation.getStatus() != StaticIPAllocationStatus.ACTIVE) {
            throw new IllegalStateException("Allocation is not active");
        }

        // Validate port
        validatePort(request.getExternalPort());

        // Check port conflict
        if (ruleRepository.isPortUsed(allocation, request.getExternalPort())) {
            throw new IllegalStateException("Port " + request.getExternalPort() + " is already in use");
        }

        // Check limits and determine if from addon
        boolean isFromAddon = false;
        int includedCount = ruleRepository.countIncludedByAllocation(allocation);
        int addonCount = ruleRepository.countAddonByAllocation(allocation);
        int totalAvailableAddons = addonRepository.sumAvailablePortsByAllocation(allocation);

        if (includedCount >= allocation.getPortForwardsIncluded()) {
            // Need to use addon slot
            if (addonCount >= totalAvailableAddons) {
                throw new IllegalStateException("Port forward limit reached. Included: " +
                        allocation.getPortForwardsIncluded() + ", Addon slots: " + totalAvailableAddons +
                        ". Purchase an addon pack for more ports.");
            }
            isFromAddon = true;

            // Increment addon usage
            List<PortForwardAddon> addonsWithSlots = addonRepository.findWithAvailableSlots(allocation);
            if (!addonsWithSlots.isEmpty()) {
                PortForwardAddon addon = addonsWithSlots.get(0);
                addon.setPortsUsed(addon.getPortsUsed() + 1);
                addonRepository.save(addon);
            }
        } else {
            // Using included slot
            allocation.setPortForwardsUsed(allocation.getPortForwardsUsed() + 1);
            allocationRepository.save(allocation);
        }

        // Create rule
        PortForwardRule rule = PortForwardRule.builder()
                .user(user)
                .allocation(allocation)
                .externalPort(request.getExternalPort())
                .internalPort(request.getInternalPort())
                .protocol(request.getProtocol())
                .status(PortForwardStatus.PENDING)
                .description(request.getDescription())
                .enabled(true)
                .isFromAddon(isFromAddon)
                .build();

        rule = ruleRepository.save(rule);
        log.info("Created port forward rule {} for user {}: {}:{} -> {}",
                rule.getId(), user.getId(), allocation.getPublicIp(),
                rule.getExternalPort(), rule.getInternalPort());

        // Trigger configuration
        triggerPortForwardConfiguration(rule);

        return rule;
    }

    /**
     * Delete a port forward rule
     */
    @Transactional
    public void deletePortForwardRule(User user, Long ruleId) {
        PortForwardRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        if (rule.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Rule does not belong to user");
        }

        if (rule.getStatus() == PortForwardStatus.DELETED) {
            throw new IllegalStateException("Rule already deleted");
        }

        // Soft delete
        rule.setStatus(PortForwardStatus.DELETED);
        rule.setEnabled(false);
        ruleRepository.save(rule);

        // Return slot
        if (Boolean.TRUE.equals(rule.getIsFromAddon())) {
            List<PortForwardAddon> addons = addonRepository.findActiveByAllocation(rule.getAllocation());
            for (PortForwardAddon addon : addons) {
                if (addon.getPortsUsed() > 0) {
                    addon.setPortsUsed(addon.getPortsUsed() - 1);
                    addonRepository.save(addon);
                    break;
                }
            }
        } else {
            StaticIPAllocation allocation = rule.getAllocation();
            if (allocation.getPortForwardsUsed() > 0) {
                allocation.setPortForwardsUsed(allocation.getPortForwardsUsed() - 1);
                allocationRepository.save(allocation);
            }
        }

        log.info("Deleted port forward rule {} for user {}", ruleId, user.getId());

        // Trigger cleanup
        triggerPortForwardCleanup(rule);
    }

    /**
     * Toggle port forward rule enabled/disabled
     */
    @Transactional
    public PortForwardRule togglePortForwardRule(User user, Long ruleId, boolean enabled) {
        PortForwardRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        if (rule.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Rule does not belong to user");
        }

        if (rule.getStatus() == PortForwardStatus.DELETED) {
            throw new IllegalStateException("Cannot toggle deleted rule");
        }

        rule.setEnabled(enabled);
        rule.setStatus(enabled ? PortForwardStatus.PENDING : PortForwardStatus.DISABLED);
        ruleRepository.save(rule);

        log.info("Toggled port forward rule {} to {} for user {}", ruleId, enabled, user.getId());

        if (enabled) {
            triggerPortForwardConfiguration(rule);
        } else {
            triggerPortForwardCleanup(rule);
        }

        return rule;
    }

    /**
     * Get all port forward rules for an allocation
     */
    public List<PortForwardRuleDTO> getPortForwardRules(User user, Long allocationId) {
        StaticIPAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found"));

        if (allocation.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Allocation does not belong to user");
        }

        return ruleRepository.findByAllocation(allocation).stream()
                .filter(r -> r.getStatus() != PortForwardStatus.DELETED)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Purchase a port forward addon pack
     */
    @Transactional
    public PortForwardAddon purchaseAddon(User user, Long allocationId, PortForwardAddonType addonType,
                                           String externalSubscriptionId) {
        StaticIPAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found"));

        if (allocation.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Allocation does not belong to user");
        }

        PortForwardAddon addon = PortForwardAddon.builder()
                .user(user)
                .allocation(allocation)
                .addonType(addonType)
                .extraPorts(addonType.getPorts())
                .portsUsed(0)
                .priceMonthly(addonType.getPriceMonthly())
                .status(SubscriptionStatus.ACTIVE)
                .externalSubscriptionId(externalSubscriptionId)
                .startedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .autoRenew(true)
                .build();

        addon = addonRepository.save(addon);
        log.info("Purchased port forward addon {} for user {}: {} ports",
                addon.getId(), user.getId(), addonType.getPorts());

        return addon;
    }

    /**
     * Cancel a port forward addon
     */
    @Transactional
    public void cancelAddon(User user, Long addonId) {
        PortForwardAddon addon = addonRepository.findById(addonId)
                .orElseThrow(() -> new IllegalArgumentException("Addon not found"));

        if (addon.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Addon does not belong to user");
        }

        addon.setAutoRenew(false);
        addon.setCancelledAt(LocalDateTime.now());
        addonRepository.save(addon);

        log.info("Cancelled port forward addon {} for user {}", addonId, user.getId());
    }

    /**
     * Get port forward limits for an allocation
     */
    public PortForwardLimits getPortForwardLimits(User user, Long allocationId) {
        StaticIPAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found"));

        if (allocation.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Allocation does not belong to user");
        }

        int includedTotal = allocation.getPortForwardsIncluded();
        int includedUsed = ruleRepository.countIncludedByAllocation(allocation);
        int addonTotal = addonRepository.sumAvailablePortsByAllocation(allocation);
        int addonUsed = ruleRepository.countAddonByAllocation(allocation);

        return PortForwardLimits.builder()
                .includedTotal(includedTotal)
                .includedUsed(includedUsed)
                .addonTotal(addonTotal)
                .addonUsed(addonUsed)
                .totalAvailable(includedTotal + addonTotal - includedUsed - addonUsed)
                .build();
    }

    /**
     * Update rule status after configuration
     */
    @Transactional
    public void updateRuleStatus(Long ruleId, PortForwardStatus status, String errorMessage) {
        PortForwardRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found"));

        rule.setStatus(status);
        if (status == PortForwardStatus.ACTIVE) {
            rule.setConfiguredAt(LocalDateTime.now());
        }
        if (errorMessage != null) {
            rule.setLastError(errorMessage);
        }
        ruleRepository.save(rule);
    }

    /**
     * Process expiring addons
     */
    @Transactional
    public void processExpiringAddons() {
        LocalDateTime now = LocalDateTime.now();
        List<PortForwardAddon> expiring = addonRepository
                .findExpiringBetween(now.minusHours(1), now);

        for (PortForwardAddon addon : expiring) {
            if (Boolean.TRUE.equals(addon.getAutoRenew())) {
                // TODO: Process renewal payment
                addon.setExpiresAt(addon.getExpiresAt().plusMonths(1));
                addonRepository.save(addon);
                log.info("Renewed port forward addon {} for user {}",
                        addon.getId(), addon.getUser().getId());
            } else {
                addon.setStatus(SubscriptionStatus.EXPIRED);
                addonRepository.save(addon);

                // Disable excess rules using addon slots
                disableExcessRules(addon.getAllocation());
                log.info("Expired port forward addon {} for user {}",
                        addon.getId(), addon.getUser().getId());
            }
        }
    }

    // Helper methods

    private void validatePort(int port) {
        if (port < MIN_ALLOWED_PORT || port > MAX_ALLOWED_PORT) {
            throw new IllegalArgumentException("Port must be between " + MIN_ALLOWED_PORT +
                    " and " + MAX_ALLOWED_PORT);
        }
        if (BLOCKED_PORTS.contains(port)) {
            throw new IllegalArgumentException("Port " + port + " is blocked for security reasons");
        }
    }

    private void disableExcessRules(StaticIPAllocation allocation) {
        int availableSlots = addonRepository.sumAvailablePortsByAllocation(allocation);
        int addonRulesCount = ruleRepository.countAddonByAllocation(allocation);

        if (addonRulesCount > availableSlots) {
            int toDisable = addonRulesCount - availableSlots;
            List<PortForwardRule> addonRules = ruleRepository.findActiveByAllocation(allocation)
                    .stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsFromAddon()))
                    .limit(toDisable)
                    .collect(Collectors.toList());

            for (PortForwardRule rule : addonRules) {
                rule.setStatus(PortForwardStatus.DISABLED);
                rule.setEnabled(false);
                ruleRepository.save(rule);
                triggerPortForwardCleanup(rule);
            }
        }
    }

    private PortForwardRuleDTO toDTO(PortForwardRule rule) {
        return PortForwardRuleDTO.builder()
                .id(rule.getId())
                .externalPort(rule.getExternalPort())
                .internalPort(rule.getInternalPort())
                .protocol(rule.getProtocol())
                .status(rule.getStatus())
                .description(rule.getDescription())
                .enabled(Boolean.TRUE.equals(rule.getEnabled()))
                .isFromAddon(Boolean.TRUE.equals(rule.getIsFromAddon()))
                .createdAt(rule.getCreatedAt())
                .build();
    }

    private void triggerPortForwardConfiguration(PortForwardRule rule) {
        // TODO: Send message to protocol server to configure port forward
        log.info("Port forward configuration triggered for rule {}", rule.getId());
    }

    private void triggerPortForwardCleanup(PortForwardRule rule) {
        // TODO: Send message to protocol server to cleanup port forward
        log.info("Port forward cleanup triggered for rule {}", rule.getId());
    }

    // Inner class for limits response
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PortForwardLimits {
        private int includedTotal;
        private int includedUsed;
        private int addonTotal;
        private int addonUsed;
        private int totalAvailable;
    }
}
