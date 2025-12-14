package com.orbvpn.api.service.staticip;

import com.orbvpn.api.domain.dto.staticip.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaticIPService {

    private final StaticIPPoolRepository poolRepository;
    private final StaticIPSubscriptionRepository subscriptionRepository;
    private final StaticIPAllocationRepository allocationRepository;
    private final OrbMeshNodeRepository nodeRepository;
    private final UserService userService;

    /**
     * Get available regions with their pricing and availability
     */
    public List<RegionAvailabilityDTO> getAvailableRegions() {
        List<Object[]> regionStats = poolRepository.countAvailableByRegion();
        return regionStats.stream()
                .map(row -> RegionAvailabilityDTO.builder()
                        .region((String) row[0])
                        .availableCount(((Number) row[1]).intValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get subscription plans with pricing
     */
    public List<StaticIPPlanDTO> getPlans() {
        return List.of(StaticIPPlanType.values()).stream()
                .map(plan -> StaticIPPlanDTO.builder()
                        .planType(plan)
                        .name(plan.getDisplayName())
                        .priceMonthly(plan.getPriceMonthly())
                        .regionsIncluded(plan.getRegionsIncluded())
                        .portForwardsPerRegion(plan.getPortForwardsPerRegion())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Create a new static IP subscription for user
     */
    @Transactional
    public StaticIPSubscription createSubscription(User user, StaticIPPlanType planType,
                                                    boolean autoRenew, String externalSubscriptionId) {
        // Check if user already has active subscription
        Optional<StaticIPSubscription> existing = subscriptionRepository.findActiveByUser(user);
        if (existing.isPresent()) {
            throw new IllegalStateException("User already has an active static IP subscription");
        }

        StaticIPSubscription subscription = StaticIPSubscription.builder()
                .user(user)
                .planType(planType)
                .status(SubscriptionStatus.ACTIVE)
                .priceMonthly(planType.getPriceMonthly())
                .regionsIncluded(planType.getRegionsIncluded())
                .portForwardsPerRegion(planType.getPortForwardsPerRegion())
                .regionsUsed(0)
                .autoRenew(autoRenew)
                .externalSubscriptionId(externalSubscriptionId)
                .startedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMonths(1))
                .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created static IP subscription {} for user {}", subscription.getId(), user.getId());
        return subscription;
    }

    /**
     * Allocate a static IP to user in specified region
     */
    @Transactional
    public StaticIPAllocation allocateStaticIP(User user, String region) {
        // Get user's subscription
        StaticIPSubscription subscription = subscriptionRepository.findActiveByUser(user)
                .orElseThrow(() -> new IllegalStateException("No active static IP subscription found"));

        // Check region limit
        if (subscription.getRegionsUsed() >= subscription.getRegionsIncluded()) {
            throw new IllegalStateException("Region limit reached for current plan. Used: " +
                    subscription.getRegionsUsed() + "/" + subscription.getRegionsIncluded());
        }

        // Check if user already has allocation in this region
        Optional<StaticIPAllocation> existingAllocation = allocationRepository
                .findByUserAndRegionAndStatus(user, region, StaticIPAllocationStatus.ACTIVE);
        if (existingAllocation.isPresent()) {
            throw new IllegalStateException("User already has a static IP in region: " + region);
        }

        // Find available IP in the pool
        List<StaticIPPool> availableIPs = poolRepository.findAvailableByRegion(region, PageRequest.of(0, 1));
        if (availableIPs.isEmpty()) {
            throw new IllegalStateException("No static IPs available in region: " + region);
        }

        StaticIPPool ipPool = availableIPs.get(0);

        // Find best node for this allocation
        List<OrbMeshNode> availableNodes = nodeRepository.findBestForStaticIp(region, PageRequest.of(0, 1));
        if (availableNodes.isEmpty()) {
            throw new IllegalStateException("No available nodes for static IP in region: " + region);
        }

        OrbMeshNode node = availableNodes.get(0);

        // Generate internal IP for NAT mapping
        String internalIp = generateInternalIP();

        // Create allocation
        StaticIPAllocation allocation = StaticIPAllocation.builder()
                .user(user)
                .subscription(subscription)
                .region(region)
                .publicIp(ipPool.getPublicIp())
                .internalIp(internalIp)
                .serverId(node.getId())
                .status(StaticIPAllocationStatus.PENDING)
                .portForwardsIncluded(subscription.getPortForwardsPerRegion())
                .portForwardsUsed(0)
                .build();

        allocation = allocationRepository.save(allocation);

        // Mark IP as allocated in pool
        ipPool.setIsAllocated(true);
        ipPool.setAllocatedAt(LocalDateTime.now());
        poolRepository.save(ipPool);

        // Update subscription region count
        subscription.setRegionsUsed(subscription.getRegionsUsed() + 1);
        subscriptionRepository.save(subscription);

        // Update node static IP count
        node.setStaticIpsUsed(node.getStaticIpsUsed() + 1);
        nodeRepository.save(node);

        log.info("Allocated static IP {} to user {} in region {}",
                allocation.getPublicIp(), user.getId(), region);

        // Trigger NAT configuration (async)
        triggerNATConfiguration(allocation);

        return allocation;
    }

    /**
     * Release a static IP allocation
     */
    @Transactional
    public void releaseStaticIP(User user, Long allocationId) {
        StaticIPAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found: " + allocationId));

        if (allocation.getUser().getId() != user.getId()) {
            throw new IllegalArgumentException("Allocation does not belong to user");
        }

        if (allocation.getStatus() == StaticIPAllocationStatus.RELEASED) {
            throw new IllegalStateException("Allocation already released");
        }

        // Mark allocation as released
        allocation.setStatus(StaticIPAllocationStatus.RELEASED);
        allocation.setReleasedAt(LocalDateTime.now());
        allocationRepository.save(allocation);

        // Return IP to pool
        poolRepository.findByPublicIp(allocation.getPublicIp())
                .ifPresent(pool -> {
                    pool.setIsAllocated(false);
                    pool.setAllocatedAt(null);
                    poolRepository.save(pool);
                });

        // Update subscription region count
        StaticIPSubscription subscription = allocation.getSubscription();
        if (subscription != null && subscription.getRegionsUsed() > 0) {
            subscription.setRegionsUsed(subscription.getRegionsUsed() - 1);
            subscriptionRepository.save(subscription);
        }

        // Update node static IP count
        nodeRepository.findById(allocation.getServerId())
                .ifPresent(node -> {
                    if (node.getStaticIpsUsed() > 0) {
                        node.setStaticIpsUsed(node.getStaticIpsUsed() - 1);
                        nodeRepository.save(node);
                    }
                });

        log.info("Released static IP {} for user {}", allocation.getPublicIp(), user.getId());

        // Trigger NAT cleanup (async)
        triggerNATCleanup(allocation);
    }

    /**
     * Get user's static IP allocations
     */
    public List<StaticIPAllocation> getUserAllocations(User user) {
        return allocationRepository.findActiveByUser(user);
    }

    /**
     * Get user's static IP subscription
     */
    public Optional<StaticIPSubscription> getUserSubscription(User user) {
        return subscriptionRepository.findActiveByUser(user);
    }

    /**
     * Cancel subscription (at end of billing period)
     */
    @Transactional
    public void cancelSubscription(User user) {
        StaticIPSubscription subscription = subscriptionRepository.findActiveByUser(user)
                .orElseThrow(() -> new IllegalStateException("No active subscription found"));

        subscription.setAutoRenew(false);
        subscription.setCancelledAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        log.info("Cancelled static IP subscription for user {}", user.getId());
    }

    /**
     * Upgrade/downgrade subscription plan
     */
    @Transactional
    public StaticIPSubscription changePlan(User user, StaticIPPlanType newPlanType) {
        StaticIPSubscription subscription = subscriptionRepository.findActiveByUser(user)
                .orElseThrow(() -> new IllegalStateException("No active subscription found"));

        // Check if downgrade would exceed new limits
        if (newPlanType.getRegionsIncluded() < subscription.getRegionsUsed()) {
            throw new IllegalStateException("Cannot downgrade: current regions (" +
                    subscription.getRegionsUsed() + ") exceed new plan limit (" +
                    newPlanType.getRegionsIncluded() + ")");
        }

        subscription.setPlanType(newPlanType);
        subscription.setPriceMonthly(newPlanType.getPriceMonthly());
        subscription.setRegionsIncluded(newPlanType.getRegionsIncluded());
        subscription.setPortForwardsPerRegion(newPlanType.getPortForwardsPerRegion());
        subscriptionRepository.save(subscription);

        // Update port forward limits on existing allocations
        List<StaticIPAllocation> allocations = allocationRepository.findActiveByUser(user);
        for (StaticIPAllocation allocation : allocations) {
            allocation.setPortForwardsIncluded(newPlanType.getPortForwardsPerRegion());
            allocationRepository.save(allocation);
        }

        log.info("Changed static IP plan for user {} to {}", user.getId(), newPlanType);
        return subscription;
    }

    /**
     * Update allocation status after NAT configuration
     */
    @Transactional
    public void updateAllocationStatus(Long allocationId, StaticIPAllocationStatus status, String errorMessage) {
        StaticIPAllocation allocation = allocationRepository.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Allocation not found"));

        allocation.setStatus(status);
        if (status == StaticIPAllocationStatus.ACTIVE) {
            allocation.setConfiguredAt(LocalDateTime.now());
        }
        if (errorMessage != null) {
            allocation.setLastError(errorMessage);
        }
        allocationRepository.save(allocation);
    }

    /**
     * Process expired subscriptions
     */
    @Transactional
    public void processExpiredSubscriptions() {
        List<StaticIPSubscription> expiring = subscriptionRepository
                .findExpiringBetween(LocalDateTime.now().minusHours(1), LocalDateTime.now());

        for (StaticIPSubscription subscription : expiring) {
            if (Boolean.TRUE.equals(subscription.getAutoRenew())) {
                // TODO: Process renewal payment
                subscription.setExpiresAt(subscription.getExpiresAt().plusMonths(1));
                subscriptionRepository.save(subscription);
                log.info("Renewed static IP subscription {} for user {}",
                        subscription.getId(), subscription.getUser().getId());
            } else {
                // Expire subscription and release allocations
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(subscription);

                List<StaticIPAllocation> allocations = allocationRepository
                        .findBySubscription(subscription);
                for (StaticIPAllocation allocation : allocations) {
                    if (allocation.getStatus() == StaticIPAllocationStatus.ACTIVE) {
                        releaseStaticIP(subscription.getUser(), allocation.getId());
                    }
                }
                log.info("Expired static IP subscription {} for user {}",
                        subscription.getId(), subscription.getUser().getId());
            }
        }
    }

    // Helper methods

    private String generateInternalIP() {
        // Generate unique internal IP in 10.x.x.x range for NAT
        // In production, this should use proper IP management
        return "10." + (100 + (int)(Math.random() * 155)) + "." +
               (int)(Math.random() * 256) + "." + (int)(Math.random() * 254 + 1);
    }

    private void triggerNATConfiguration(StaticIPAllocation allocation) {
        // TODO: Send message to protocol server to configure NAT
        // This will be implemented with the protocol server integration
        log.info("NAT configuration triggered for allocation {}", allocation.getId());
    }

    private void triggerNATCleanup(StaticIPAllocation allocation) {
        // TODO: Send message to protocol server to cleanup NAT rules
        log.info("NAT cleanup triggered for allocation {}", allocation.getId());
    }
}
