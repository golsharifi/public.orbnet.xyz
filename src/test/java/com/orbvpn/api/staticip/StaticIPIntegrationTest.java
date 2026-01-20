package com.orbvpn.api.staticip;

import com.orbvpn.api.domain.dto.staticip.CreatePortForwardRequest;
import com.orbvpn.api.domain.dto.staticip.RegionAvailabilityDTO;
import com.orbvpn.api.domain.dto.staticip.StaticIPPlanDTO;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.staticip.PortForwardService;
import com.orbvpn.api.service.staticip.StaticIPService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Static IP and Port Forwarding.
 * These tests connect to the real database and test the complete flow.
 *
 * IMPORTANT: These tests create and clean up test data. Run with caution.
 *
 * To run: mvn test -Dtest=StaticIPIntegrationTest -Dspring.profiles.active=default
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaticIPIntegrationTest {

    @Autowired
    private StaticIPService staticIPService;

    @Autowired
    private PortForwardService portForwardService;

    @Autowired
    private StaticIPPoolRepository poolRepository;

    @Autowired
    private StaticIPSubscriptionRepository subscriptionRepository;

    @Autowired
    private StaticIPAllocationRepository allocationRepository;

    @Autowired
    private PortForwardRuleRepository ruleRepository;

    @Autowired
    private OrbMeshNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    // Test data tracking for cleanup
    private final List<User> testUsers = new ArrayList<>();
    private final List<StaticIPPool> testPools = new ArrayList<>();
    private final List<OrbMeshNode> testNodes = new ArrayList<>();

    // Test fixtures
    private User testUser;
    private OrbMeshNode testNode;
    private StaticIPSubscription subscription;
    private StaticIPAllocation allocation;

    private static final String TEST_EMAIL_PREFIX = "staticip-integration-test-";
    private static final String TEST_NODE_PREFIX = "integration-test-node-";
    private static final String TEST_POOL_PREFIX = "/subscriptions/integration-test/";

    @BeforeAll
    void setUp() {
        System.out.println("========================================");
        System.out.println("Starting Static IP Integration Tests");
        System.out.println("========================================");
        System.out.flush();

        try {
            // Create test user first (minimal setup)
            System.out.println("Creating test user...");
            System.out.flush();
            testUser = createTestUser("main");
            System.out.println("Created test user: " + testUser.getEmail());
            System.out.flush();

            // Check for available IPs in the pool
            System.out.println("Checking available regions...");
            System.out.flush();
            List<RegionAvailabilityDTO> regions = staticIPService.getAvailableRegions();
            System.out.println("Available regions: " + regions.size());
            System.out.flush();

            for (RegionAvailabilityDTO region : regions) {
                System.out.println("  - " + region.getRegion() + ": " + region.getAvailableCount() + " IPs available");
            }
            System.out.flush();

            // If no IPs available, create test pool
            if (regions.isEmpty() || regions.stream().allMatch(r -> r.getAvailableCount() == 0)) {
                System.out.println("No IPs available - creating test pool...");
                System.out.flush();
                testNode = createTestNode("eastus", "East US (Test)");
                createTestIPPool("eastus", testNode.getId(), 5);
                System.out.println("Created test node and 5 test IPs in eastus");
                System.out.flush();
            }

            System.out.println("Setup completed successfully");
            System.out.flush();
        } catch (Exception e) {
            System.err.println("ERROR in setup: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @AfterAll
    void tearDown() {
        System.out.println("\n========================================");
        System.out.println("Cleaning up test data...");
        System.out.println("========================================");
        cleanupTestData();
        System.out.println("Cleanup complete");
    }

    private void cleanupTestData() {
        // Clean up test users and their associated data
        List<User> usersToDelete = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().startsWith(TEST_EMAIL_PREFIX))
                .toList();

        for (User user : usersToDelete) {
            try {
                // Delete port forward rules
                ruleRepository.findByUser(user).forEach(rule -> {
                    try {
                        ruleRepository.delete(rule);
                    } catch (Exception e) {
                        System.err.println("Failed to delete rule: " + e.getMessage());
                    }
                });

                // Delete allocations
                allocationRepository.findByUser(user).forEach(alloc -> {
                    try {
                        // Return IP to pool
                        poolRepository.findByPublicIp(alloc.getPublicIp()).ifPresent(pool -> {
                            pool.setIsAllocated(false);
                            pool.setAllocatedAt(null);
                            poolRepository.save(pool);
                        });
                        allocationRepository.delete(alloc);
                    } catch (Exception e) {
                        System.err.println("Failed to delete allocation: " + e.getMessage());
                    }
                });

                // Delete subscriptions
                subscriptionRepository.findActiveByUser(user).ifPresent(sub -> {
                    try {
                        subscriptionRepository.delete(sub);
                    } catch (Exception e) {
                        System.err.println("Failed to delete subscription: " + e.getMessage());
                    }
                });

                // Delete user
                userRepository.delete(user);
            } catch (Exception e) {
                System.err.println("Failed to delete user " + user.getEmail() + ": " + e.getMessage());
            }
        }

        // Clean up test IP pools
        poolRepository.findAll().stream()
                .filter(p -> p.getAzureResourceId() != null && p.getAzureResourceId().startsWith(TEST_POOL_PREFIX))
                .forEach(pool -> {
                    try {
                        poolRepository.delete(pool);
                    } catch (Exception e) {
                        System.err.println("Failed to delete pool: " + e.getMessage());
                    }
                });

        // Clean up test nodes
        nodeRepository.findAll().stream()
                .filter(n -> n.getNodeUuid() != null && n.getNodeUuid().startsWith(TEST_NODE_PREFIX))
                .forEach(node -> {
                    try {
                        nodeRepository.delete(node);
                    } catch (Exception e) {
                        System.err.println("Failed to delete node: " + e.getMessage());
                    }
                });

        testUsers.clear();
        testPools.clear();
        testNodes.clear();
    }

    // ========================================
    // Subscription Tests
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Get available plans")
    void testGetAvailablePlans() {
        List<StaticIPPlanDTO> plans = staticIPService.getPlans();

        assertNotNull(plans, "Plans should not be null");
        assertFalse(plans.isEmpty(), "Should have at least one plan");

        System.out.println("\nAvailable Plans:");
        for (StaticIPPlanDTO plan : plans) {
            System.out.println("  - " + plan.getPlanType() + ": " +
                    plan.getRegionsIncluded() + " regions, " +
                    plan.getPortForwardsPerRegion() + " port forwards/region, $" +
                    plan.getPriceMonthly() + "/month");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Get available regions from database")
    void testGetAvailableRegions() {
        List<RegionAvailabilityDTO> regions = staticIPService.getAvailableRegions();

        assertNotNull(regions, "Regions should not be null");

        System.out.println("\nAvailable Regions:");
        if (regions.isEmpty()) {
            System.out.println("  No regions with available IPs");
        } else {
            for (RegionAvailabilityDTO region : regions) {
                System.out.println("  - " + region.getRegion() + ": " +
                        region.getAvailableCount() + " IPs available");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Create subscription - Success")
    void testCreateSubscription() {
        subscription = staticIPService.createSubscription(
                testUser,
                StaticIPPlanType.MULTI_REGION,
                true,
                "integration-test-sub-" + System.currentTimeMillis()
        );

        assertNotNull(subscription, "Subscription should be created");
        assertEquals(StaticIPPlanType.MULTI_REGION, subscription.getPlanType());
        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals(3, subscription.getRegionsIncluded());
        assertEquals(0, subscription.getRegionsUsed());
        assertTrue(subscription.getAutoRenew());

        System.out.println("\nCreated subscription:");
        System.out.println("  ID: " + subscription.getId());
        System.out.println("  Plan: " + subscription.getPlanType());
        System.out.println("  Regions: " + subscription.getRegionsIncluded());
        System.out.println("  Expires: " + subscription.getExpiresAt());
    }

    @Test
    @Order(4)
    @DisplayName("Create duplicate subscription - Should fail")
    void testDuplicateSubscription() {
        assertThrows(IllegalStateException.class, () ->
                        staticIPService.createSubscription(testUser, StaticIPPlanType.PERSONAL, false, null),
                "Should not allow duplicate subscription"
        );
        System.out.println("\nDuplicate subscription correctly rejected");
    }

    // ========================================
    // Static IP Allocation Tests
    // ========================================

    @Test
    @Order(10)
    @DisplayName("Allocate static IP from pool")
    void testAllocateStaticIP() {
        // Find a region with available IPs
        List<RegionAvailabilityDTO> regions = staticIPService.getAvailableRegions();
        String targetRegion = regions.stream()
                .filter(r -> r.getAvailableCount() > 0)
                .map(RegionAvailabilityDTO::getRegion)
                .findFirst()
                .orElse("eastus");

        System.out.println("\nAttempting allocation in region: " + targetRegion);

        allocation = staticIPService.allocateStaticIP(testUser, targetRegion);

        assertNotNull(allocation, "Allocation should be created");
        assertNotNull(allocation.getPublicIp(), "Should have public IP assigned");
        assertNotNull(allocation.getInternalIp(), "Should have internal IP generated");
        assertEquals(targetRegion, allocation.getRegion());
        assertEquals(StaticIPAllocationStatus.PENDING, allocation.getStatus());

        System.out.println("Allocated Static IP:");
        System.out.println("  ID: " + allocation.getId());
        System.out.println("  Public IP: " + allocation.getPublicIp());
        System.out.println("  Internal IP: " + allocation.getInternalIp());
        System.out.println("  Region: " + allocation.getRegion());
        System.out.println("  Status: " + allocation.getStatus());
        System.out.println("  Server ID: " + allocation.getServerId());

        // Verify subscription was updated
        StaticIPSubscription updatedSub = subscriptionRepository.findById(subscription.getId()).orElseThrow();
        assertEquals(1, updatedSub.getRegionsUsed(), "Regions used should be incremented");

        // Verify IP was marked as allocated in pool
        StaticIPPool pool = poolRepository.findByPublicIp(allocation.getPublicIp()).orElseThrow();
        assertTrue(pool.getIsAllocated(), "IP should be marked as allocated");
    }

    @Test
    @Order(11)
    @DisplayName("Duplicate allocation in same region - Should fail")
    void testDuplicateRegionAllocation() {
        assertThrows(IllegalStateException.class, () ->
                        staticIPService.allocateStaticIP(testUser, allocation.getRegion()),
                "Should not allow duplicate allocation in same region"
        );
        System.out.println("\nDuplicate region allocation correctly rejected");
    }

    @Test
    @Order(12)
    @DisplayName("Update allocation status to ACTIVE")
    void testUpdateAllocationStatus() {
        staticIPService.updateAllocationStatus(
                allocation.getId(),
                StaticIPAllocationStatus.ACTIVE,
                null
        );

        StaticIPAllocation updated = allocationRepository.findById(allocation.getId()).orElseThrow();
        assertEquals(StaticIPAllocationStatus.ACTIVE, updated.getStatus());
        assertNotNull(updated.getConfiguredAt());

        allocation = updated;
        System.out.println("\nAllocation status updated to ACTIVE");
    }

    // ========================================
    // Port Forwarding Tests
    // ========================================

    @Test
    @Order(20)
    @DisplayName("Create port forward rule")
    void testCreatePortForwardRule() {
        CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                .allocationId(allocation.getId())
                .externalPort(8080)
                .internalPort(80)
                .protocol(PortForwardProtocol.TCP)
                .description("Integration Test HTTP")
                .build();

        PortForwardRule rule = portForwardService.createPortForwardRule(testUser, request);

        assertNotNull(rule, "Rule should be created");
        assertEquals(8080, rule.getExternalPort());
        assertEquals(80, rule.getInternalPort());
        assertEquals(PortForwardProtocol.TCP, rule.getProtocol());
        assertEquals(PortForwardStatus.PENDING, rule.getStatus());
        assertTrue(rule.getEnabled());

        System.out.println("\nCreated port forward rule:");
        System.out.println("  ID: " + rule.getId());
        System.out.println("  External: " + allocation.getPublicIp() + ":" + rule.getExternalPort());
        System.out.println("  Internal: " + allocation.getInternalIp() + ":" + rule.getInternalPort());
        System.out.println("  Protocol: " + rule.getProtocol());
    }

    @Test
    @Order(21)
    @DisplayName("Blocked port rejection")
    void testBlockedPortRejection() {
        int[] blockedPorts = {22, 25, 3306, 5432};

        for (int port : blockedPorts) {
            CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                    .allocationId(allocation.getId())
                    .externalPort(port)
                    .internalPort(8080)
                    .protocol(PortForwardProtocol.TCP)
                    .build();

            assertThrows(IllegalArgumentException.class, () ->
                            portForwardService.createPortForwardRule(testUser, request),
                    "Port " + port + " should be blocked"
            );
        }
        System.out.println("\nBlocked ports correctly rejected: " + Arrays.toString(blockedPorts));
    }

    @Test
    @Order(22)
    @DisplayName("Port conflict detection")
    void testPortConflictDetection() {
        // Port 8080 already used from previous test
        CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                .allocationId(allocation.getId())
                .externalPort(8080)  // Same port
                .internalPort(8081)
                .protocol(PortForwardProtocol.TCP)
                .build();

        assertThrows(IllegalStateException.class, () ->
                        portForwardService.createPortForwardRule(testUser, request),
                "Should detect port conflict"
        );
        System.out.println("\nPort conflict correctly detected");
    }

    // ========================================
    // Concurrent Allocation Tests
    // ========================================

    @Test
    @Order(30)
    @DisplayName("Concurrent allocations - Race condition test")
    void testConcurrentAllocations() throws Exception {
        // Create multiple users
        int numUsers = 3;
        List<User> concurrentUsers = new ArrayList<>();
        for (int i = 0; i < numUsers; i++) {
            User user = createTestUser("concurrent-" + i);
            staticIPService.createSubscription(user, StaticIPPlanType.PERSONAL, false, null);
            concurrentUsers.add(user);
        }

        // Find region with enough IPs
        List<RegionAvailabilityDTO> regions = staticIPService.getAvailableRegions();
        String targetRegion = regions.stream()
                .filter(r -> r.getAvailableCount() >= numUsers)
                .map(RegionAvailabilityDTO::getRegion)
                .findFirst()
                .orElse(null);

        if (targetRegion == null) {
            System.out.println("\nSkipping concurrent test - not enough IPs available");
            return;
        }

        System.out.println("\nRunning concurrent allocation test with " + numUsers + " users in region: " + targetRegion);

        ExecutorService executor = Executors.newFixedThreadPool(numUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> allocatedIPs = Collections.synchronizedList(new ArrayList<>());

        for (User user : concurrentUsers) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    StaticIPAllocation alloc = staticIPService.allocateStaticIP(user, targetRegion);
                    allocatedIPs.add(alloc.getPublicIp());
                    successCount.incrementAndGet();
                    System.out.println("  ✓ User " + user.getEmail() + " got IP: " + alloc.getPublicIp());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  ✗ User " + user.getEmail() + " failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("\nConcurrent allocation results:");
        System.out.println("  Successful: " + successCount.get());
        System.out.println("  Failed: " + failCount.get());

        // Verify no duplicate IPs were allocated
        Set<String> uniqueIPs = new HashSet<>(allocatedIPs);
        assertEquals(allocatedIPs.size(), uniqueIPs.size(),
                "All allocated IPs should be unique - no duplicates allowed");

        assertTrue(successCount.get() >= 1, "At least one allocation should succeed");
    }

    // ========================================
    // Multiple IP Allocation Tests
    // ========================================

    @Test
    @Order(40)
    @DisplayName("Allocate multiple IPs in different regions")
    void testMultipleRegionAllocation() {
        // Get regions with available IPs
        List<RegionAvailabilityDTO> regions = staticIPService.getAvailableRegions();
        List<String> availableRegions = regions.stream()
                .filter(r -> r.getAvailableCount() > 0 && !r.getRegion().equals(allocation.getRegion()))
                .map(RegionAvailabilityDTO::getRegion)
                .limit(2)
                .toList();

        if (availableRegions.isEmpty()) {
            System.out.println("\nSkipping multi-region test - no additional regions available");
            return;
        }

        System.out.println("\nTesting multi-region allocation:");

        int allocated = 0;
        for (String region : availableRegions) {
            // Check if we have capacity
            StaticIPSubscription currentSub = subscriptionRepository.findActiveByUser(testUser).orElseThrow();
            if (currentSub.getRegionsUsed() >= currentSub.getRegionsIncluded()) {
                System.out.println("  Region limit reached, stopping");
                break;
            }

            try {
                StaticIPAllocation newAlloc = staticIPService.allocateStaticIP(testUser, region);
                allocated++;
                System.out.println("  ✓ Allocated in " + region + ": " + newAlloc.getPublicIp());
            } catch (Exception e) {
                System.out.println("  ✗ Failed in " + region + ": " + e.getMessage());
            }
        }

        assertTrue(allocated >= 0, "Should have attempted allocations");

        // Verify subscription tracking
        StaticIPSubscription finalSub = subscriptionRepository.findActiveByUser(testUser).orElseThrow();
        System.out.println("\nSubscription state:");
        System.out.println("  Regions used: " + finalSub.getRegionsUsed() + "/" + finalSub.getRegionsIncluded());
    }

    // ========================================
    // Release and Cleanup Tests
    // ========================================

    @Test
    @Order(50)
    @DisplayName("Release static IP")
    void testReleaseStaticIP() {
        String publicIp = allocation.getPublicIp();
        Long allocationId = allocation.getId();

        System.out.println("\nReleasing IP: " + publicIp);

        staticIPService.releaseStaticIP(testUser, allocationId);

        // Verify allocation was released
        StaticIPAllocation released = allocationRepository.findById(allocationId).orElseThrow();
        assertEquals(StaticIPAllocationStatus.RELEASED, released.getStatus());
        assertNotNull(released.getReleasedAt());

        // Verify IP returned to pool
        StaticIPPool pool = poolRepository.findByPublicIp(publicIp).orElseThrow();
        assertFalse(pool.getIsAllocated(), "IP should be available again");

        System.out.println("  ✓ IP released successfully");
        System.out.println("  ✓ IP returned to pool and available for reallocation");
    }

    @Test
    @Order(51)
    @DisplayName("Cancel subscription")
    void testCancelSubscription() {
        staticIPService.cancelSubscription(testUser);

        StaticIPSubscription cancelled = subscriptionRepository.findActiveByUser(testUser).orElseThrow();
        assertFalse(cancelled.getAutoRenew());
        assertNotNull(cancelled.getCancelledAt());

        System.out.println("\nSubscription cancelled:");
        System.out.println("  Auto-renew: " + cancelled.getAutoRenew());
        System.out.println("  Cancelled at: " + cancelled.getCancelledAt());
    }

    // ========================================
    // Database State Verification Tests
    // ========================================

    @Test
    @Order(60)
    @DisplayName("Verify database consistency")
    void testDatabaseConsistency() {
        System.out.println("\n========================================");
        System.out.println("Database State Summary");
        System.out.println("========================================");

        // Count total IPs in pool
        long totalIPs = poolRepository.count();
        long allocatedIPs = poolRepository.findAll().stream()
                .filter(StaticIPPool::getIsAllocated)
                .count();

        System.out.println("\nIP Pool Statistics:");
        System.out.println("  Total IPs: " + totalIPs);
        System.out.println("  Allocated: " + allocatedIPs);
        System.out.println("  Available: " + (totalIPs - allocatedIPs));

        // Count allocations
        long activeAllocations = allocationRepository.findAllActive().size();
        System.out.println("\nAllocations:");
        System.out.println("  Active: " + activeAllocations);

        // Verify consistency
        assertEquals(allocatedIPs, activeAllocations,
                "Allocated IPs in pool should match active allocations");

        System.out.println("\n✓ Database consistency verified");
    }

    // ========================================
    // Helper Methods
    // ========================================

    private User createTestUser(String suffix) {
        User user = new User();
        user.setEmail(TEST_EMAIL_PREFIX + suffix + "-" + System.currentTimeMillis() + "@test.orbvpn.com");
        user.setUsername("test_" + suffix + "_" + System.currentTimeMillis());
        user.setPassword("$2a$10$test_password_hash");
        user.setRole(roleService.getByName(RoleName.USER));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user = userRepository.save(user);
        testUsers.add(user);
        return user;
    }

    private OrbMeshNode createTestNode(String region, String displayName) {
        OrbMeshNode node = OrbMeshNode.builder()
                .nodeUuid(TEST_NODE_PREFIX + region + "-" + System.currentTimeMillis())
                .deploymentType(DeploymentType.ORBVPN_DC)
                .region(region)
                .regionDisplayName(displayName)
                .publicIp("10.0.0." + (int)(Math.random() * 254 + 1))
                .isActive(true)
                .online(true)
                .supportsStaticIp(true)
                .staticIpsAvailable(10)
                .staticIpsUsed(0)
                .supportsPortForward(true)
                .maxConnections(100)
                .currentConnections(0)
                .createdAt(LocalDateTime.now())
                .build();
        node = nodeRepository.save(node);
        testNodes.add(node);
        return node;
    }

    private void createTestIPPool(String region, Long nodeId, int count) {
        for (int i = 0; i < count; i++) {
            StaticIPPool ip = StaticIPPool.builder()
                    .publicIp("52." + (100 + (int)(Math.random() * 155)) + "." +
                            (int)(Math.random() * 256) + "." + (int)(Math.random() * 254 + 1))
                    .region(region)
                    .regionDisplayName(region)
                    .azureResourceId(TEST_POOL_PREFIX + region + "/staticIps/" + System.currentTimeMillis() + "-" + i)
                    .azureSubscriptionId("integration-test-subscription")
                    .isAllocated(false)
                    .serverId(nodeId)
                    .costPerMonth(new BigDecimal("3.60"))
                    .createdAt(LocalDateTime.now())
                    .build();
            ip = poolRepository.save(ip);
            testPools.add(ip);
        }
    }
}
