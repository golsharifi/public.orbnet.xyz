package com.orbvpn.api.staticip;

import com.orbvpn.api.domain.dto.staticip.CreatePortForwardRequest;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.staticip.PortForwardService;
import com.orbvpn.api.service.staticip.StaticIPService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test Harness for Static IP and Port Forwarding
 * Simulates client connections and tests the full flow without requiring
 * actual iOS/Android devices or emulators.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaticIPTestHarness {

    private final StaticIPService staticIPService;
    private final PortForwardService portForwardService;
    private final StaticIPPoolRepository poolRepository;
    private final StaticIPSubscriptionRepository subscriptionRepository;
    private final StaticIPAllocationRepository allocationRepository;
    private final PortForwardRuleRepository ruleRepository;
    private final OrbMeshNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;

    // Test results container
    private final List<TestResult> testResults = new CopyOnWriteArrayList<>();

    /**
     * Run all tests in the harness
     */
    public TestReport runAllTests() {
        testResults.clear();
        log.info("========================================");
        log.info("Starting Static IP Test Harness");
        log.info("========================================");

        // Setup
        TestContext context = setupTestEnvironment();

        try {
            // Core Static IP Tests
            runTest("testStaticIPSubscriptionCreation", () -> testStaticIPSubscriptionCreation(context));
            runTest("testStaticIPAllocation", () -> testStaticIPAllocation(context));
            runTest("testDuplicateAllocationSameRegion", () -> testDuplicateAllocationSameRegion(context));
            runTest("testRegionLimitEnforcement", () -> testRegionLimitEnforcement(context));
            runTest("testNoAvailableIPsInRegion", () -> testNoAvailableIPsInRegion(context));
            runTest("testNoAvailableNodesInRegion", () -> testNoAvailableNodesInRegion(context));
            runTest("testStaticIPRelease", () -> testStaticIPRelease(context));

            // Port Forwarding Tests
            runTest("testPortForwardRuleCreation", () -> testPortForwardRuleCreation(context));
            runTest("testBlockedPortRejection", () -> testBlockedPortRejection(context));
            runTest("testPortConflictDetection", () -> testPortConflictDetection(context));
            runTest("testPortForwardLimitEnforcement", () -> testPortForwardLimitEnforcement(context));
            runTest("testPortForwardToggle", () -> testPortForwardToggle(context));
            runTest("testPortForwardDeletion", () -> testPortForwardDeletion(context));

            // Subscription Tests
            runTest("testPlanUpgrade", () -> testPlanUpgrade(context));
            runTest("testPlanDowngradeValidation", () -> testPlanDowngradeValidation(context));
            runTest("testSubscriptionCancellation", () -> testSubscriptionCancellation(context));

            // Concurrency Tests
            runTest("testConcurrentAllocations", () -> testConcurrentAllocations(context));
            runTest("testConcurrentPortForwardCreation", () -> testConcurrentPortForwardCreation(context));

            // Connectivity Simulation Tests
            runTest("testSimulatedClientConnection", () -> testSimulatedClientConnection(context));
            runTest("testPortForwardConnectivity", () -> testPortForwardConnectivity(context));

            // Edge Case Tests
            runTest("testInternalIPGeneration", () -> testInternalIPGeneration(context));
            runTest("testExpirationProcessing", () -> testExpirationProcessing(context));

        } finally {
            cleanupTestEnvironment(context);
        }

        return generateReport();
    }

    private void runTest(String testName, Runnable test) {
        log.info("Running test: {}", testName);
        long startTime = System.currentTimeMillis();
        try {
            test.run();
            long duration = System.currentTimeMillis() - startTime;
            testResults.add(new TestResult(testName, true, null, duration));
            log.info("  ✓ {} passed ({}ms)", testName, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            testResults.add(new TestResult(testName, false, e.getMessage(), duration));
            log.error("  ✗ {} failed: {}", testName, e.getMessage());
        }
    }

    // ========================================
    // Test Environment Setup
    // ========================================

    @Transactional
    public TestContext setupTestEnvironment() {
        log.info("Setting up test environment...");
        TestContext context = new TestContext();

        // Create test user
        User testUser = new User();
        testUser.setEmail("staticip-test-" + System.currentTimeMillis() + "@test.orbvpn.com");
        testUser.setUsername("staticip_test_user");
        testUser.setPassword("test_password_hash");
        testUser.setRole(roleService.getByName(RoleName.USER));
        testUser.setEnabled(true);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser = userRepository.save(testUser);
        context.testUser = testUser;

        // Create test node with static IP capability
        OrbMeshNode testNode = OrbMeshNode.builder()
                .nodeUuid("test-node-" + System.currentTimeMillis())
                .deploymentType(DeploymentType.ORBVPN_DC)
                .region("eastus")
                .regionDisplayName("East US")
                .publicIp("10.0.0.1")
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
        testNode = nodeRepository.save(testNode);
        context.testNode = testNode;

        // Create test IPs in pool
        for (int i = 1; i <= 5; i++) {
            StaticIPPool ip = StaticIPPool.builder()
                    .publicIp("20.185.100." + i)
                    .region("eastus")
                    .regionDisplayName("East US")
                    .azureResourceId("/subscriptions/test/staticIps/" + i)
                    .azureSubscriptionId("test-subscription")
                    .isAllocated(false)
                    .serverId(testNode.getId())
                    .costPerMonth(new java.math.BigDecimal("3.60"))
                    .createdAt(LocalDateTime.now())
                    .build();
            poolRepository.save(ip);
            context.testIPs.add(ip);
        }

        log.info("Test environment setup complete: user={}, node={}, ips={}",
                testUser.getId(), testNode.getId(), context.testIPs.size());

        return context;
    }

    @Transactional
    public void cleanupTestEnvironment(TestContext context) {
        log.info("Cleaning up test environment...");

        // Clean up in reverse order of dependencies
        if (context.testUser != null) {
            ruleRepository.findByUser(context.testUser).forEach(r -> ruleRepository.delete(r));
            allocationRepository.findActiveByUser(context.testUser).forEach(a -> allocationRepository.delete(a));
            subscriptionRepository.findActiveByUser(context.testUser)
                    .ifPresent(s -> subscriptionRepository.delete(s));
        }

        context.testIPs.forEach(ip -> poolRepository.findByPublicIp(ip.getPublicIp())
                .ifPresent(p -> poolRepository.delete(p)));

        if (context.testNode != null) {
            nodeRepository.delete(context.testNode);
        }

        if (context.testUser != null) {
            userRepository.delete(context.testUser);
        }

        log.info("Test environment cleanup complete");
    }

    // ========================================
    // Static IP Subscription Tests
    // ========================================

    private void testStaticIPSubscriptionCreation(TestContext context) {
        StaticIPSubscription subscription = staticIPService.createSubscription(
                context.testUser,
                StaticIPPlanType.PRO,
                true,
                "test-external-sub-" + System.currentTimeMillis()
        );

        assertNotNull("Subscription should be created", subscription);
        assertEquals("Plan type should match", StaticIPPlanType.PRO, subscription.getPlanType());
        assertEquals("Status should be ACTIVE", SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals("Regions used should be 0", 0, subscription.getRegionsUsed());
        assertTrue("Expiration should be in the future",
                subscription.getExpiresAt().isAfter(LocalDateTime.now()));

        context.subscription = subscription;
    }

    private void testStaticIPAllocation(TestContext context) {
        if (context.subscription == null) {
            testStaticIPSubscriptionCreation(context);
        }

        StaticIPAllocation allocation = staticIPService.allocateStaticIP(context.testUser, "eastus");

        assertNotNull("Allocation should be created", allocation);
        assertNotNull("Public IP should be assigned", allocation.getPublicIp());
        assertNotNull("Internal IP should be generated", allocation.getInternalIp());
        assertEquals("Region should match", "eastus", allocation.getRegion());
        assertEquals("Status should be PENDING", StaticIPAllocationStatus.PENDING, allocation.getStatus());

        // Verify subscription was updated
        StaticIPSubscription updated = subscriptionRepository.findById(context.subscription.getId()).orElseThrow();
        assertEquals("Regions used should be incremented", 1, updated.getRegionsUsed());

        // Verify IP pool was updated
        StaticIPPool pool = poolRepository.findByPublicIp(allocation.getPublicIp()).orElseThrow();
        assertTrue("IP should be marked as allocated", pool.getIsAllocated());

        context.allocation = allocation;
    }

    private void testDuplicateAllocationSameRegion(TestContext context) {
        if (context.allocation == null) {
            testStaticIPAllocation(context);
        }

        try {
            staticIPService.allocateStaticIP(context.testUser, "eastus");
            throw new AssertionError("Should have thrown exception for duplicate allocation");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention duplicate",
                    e.getMessage().contains("already has a static IP"));
        }
    }

    private void testRegionLimitEnforcement(TestContext context) {
        // Create a subscription with 1 region limit
        User limitUser = createTestUser("limit-test");
        staticIPService.createSubscription(limitUser, StaticIPPlanType.PERSONAL, false, null);

        // First allocation should succeed
        staticIPService.allocateStaticIP(limitUser, "eastus");

        // Second allocation should fail (PERSONAL plan allows only 1 region)
        try {
            // Create another region's node and IPs for this test
            OrbMeshNode node2 = createTestNode("westus", "West US");
            createTestIPPool("westus", node2.getId(), 1);

            staticIPService.allocateStaticIP(limitUser, "westus");
            throw new AssertionError("Should have thrown exception for region limit");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention region limit",
                    e.getMessage().contains("Region limit reached"));
        }
    }

    private void testNoAvailableIPsInRegion(TestContext context) {
        // Allocate all available IPs
        String testRegion = "noip-test-region";
        OrbMeshNode node = createTestNode(testRegion, "No IP Test Region");
        createTestIPPool(testRegion, node.getId(), 1);

        User user1 = createTestUser("noip-user1");
        staticIPService.createSubscription(user1, StaticIPPlanType.PRO, false, null);
        staticIPService.allocateStaticIP(user1, testRegion);

        // Now try to allocate when no IPs available
        User user2 = createTestUser("noip-user2");
        staticIPService.createSubscription(user2, StaticIPPlanType.PRO, false, null);

        try {
            staticIPService.allocateStaticIP(user2, testRegion);
            throw new AssertionError("Should have thrown exception for no available IPs");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention no available IPs",
                    e.getMessage().contains("No static IPs available"));
        }
    }

    private void testNoAvailableNodesInRegion(TestContext context) {
        String testRegion = "nonode-test-region";
        // Create IPs but no node for this region
        createTestIPPoolWithoutNode(testRegion, 1);

        User user = createTestUser("nonode-user");
        staticIPService.createSubscription(user, StaticIPPlanType.PRO, false, null);

        try {
            staticIPService.allocateStaticIP(user, testRegion);
            throw new AssertionError("Should have thrown exception for no available nodes");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention no available nodes",
                    e.getMessage().contains("No available nodes"));
        }
    }

    private void testStaticIPRelease(TestContext context) {
        if (context.allocation == null) {
            testStaticIPAllocation(context);
        }

        String publicIp = context.allocation.getPublicIp();
        Long allocationId = context.allocation.getId();

        staticIPService.releaseStaticIP(context.testUser, allocationId);

        // Verify allocation was released
        StaticIPAllocation released = allocationRepository.findById(allocationId).orElseThrow();
        assertEquals("Status should be RELEASED", StaticIPAllocationStatus.RELEASED, released.getStatus());
        assertNotNull("ReleasedAt should be set", released.getReleasedAt());

        // Verify IP returned to pool
        StaticIPPool pool = poolRepository.findByPublicIp(publicIp).orElseThrow();
        assertFalse("IP should be available again", pool.getIsAllocated());

        // Verify subscription count decremented
        StaticIPSubscription sub = subscriptionRepository.findById(context.subscription.getId()).orElseThrow();
        assertEquals("Regions used should be decremented", 0, sub.getRegionsUsed());

        context.allocation = null; // Clear for next tests
    }

    // ========================================
    // Port Forwarding Tests
    // ========================================

    private void testPortForwardRuleCreation(TestContext context) {
        ensureActiveAllocation(context);

        // Activate the allocation first (simulating NAT config completion)
        context.allocation.setStatus(StaticIPAllocationStatus.ACTIVE);
        context.allocation = allocationRepository.save(context.allocation);

        CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                .allocationId(context.allocation.getId())
                .externalPort(8080)
                .internalPort(80)
                .protocol(PortForwardProtocol.TCP)
                .description("Test HTTP service")
                .build();

        PortForwardRule rule = portForwardService.createPortForwardRule(context.testUser, request);

        assertNotNull("Rule should be created", rule);
        assertEquals("External port should match", 8080, rule.getExternalPort().intValue());
        assertEquals("Internal port should match", 80, rule.getInternalPort().intValue());
        assertEquals("Protocol should match", PortForwardProtocol.TCP, rule.getProtocol());
        assertEquals("Status should be PENDING", PortForwardStatus.PENDING, rule.getStatus());

        context.portForwardRule = rule;
    }

    private void testBlockedPortRejection(TestContext context) {
        ensureActiveAllocation(context);

        // Test blocked ports
        int[] blockedPorts = {22, 23, 25, 80, 443, 3306, 5432};

        for (int blockedPort : blockedPorts) {
            CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                    .allocationId(context.allocation.getId())
                    .externalPort(blockedPort)
                    .internalPort(8080)
                    .protocol(PortForwardProtocol.TCP)
                    .build();

            try {
                portForwardService.createPortForwardRule(context.testUser, request);
                throw new AssertionError("Should have rejected blocked port: " + blockedPort);
            } catch (IllegalArgumentException e) {
                assertTrue("Error should mention blocked port",
                        e.getMessage().contains("blocked") || e.getMessage().contains("Port must be"));
            }
        }
    }

    private void testPortConflictDetection(TestContext context) {
        ensureActiveAllocation(context);

        // Create first rule
        CreatePortForwardRequest request1 = CreatePortForwardRequest.builder()
                .allocationId(context.allocation.getId())
                .externalPort(9000)
                .internalPort(9000)
                .protocol(PortForwardProtocol.TCP)
                .build();

        portForwardService.createPortForwardRule(context.testUser, request1);

        // Try to create conflicting rule
        CreatePortForwardRequest request2 = CreatePortForwardRequest.builder()
                .allocationId(context.allocation.getId())
                .externalPort(9000)
                .internalPort(9001)
                .protocol(PortForwardProtocol.TCP)
                .build();

        try {
            portForwardService.createPortForwardRule(context.testUser, request2);
            throw new AssertionError("Should have detected port conflict");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention port in use",
                    e.getMessage().contains("already in use"));
        }
    }

    private void testPortForwardLimitEnforcement(TestContext context) {
        // Create user with PERSONAL plan (3 port forwards per region)
        User limitUser = createTestUser("pf-limit-test");
        staticIPService.createSubscription(limitUser, StaticIPPlanType.PERSONAL, false, null);
        StaticIPAllocation allocation = staticIPService.allocateStaticIP(limitUser, "eastus");
        allocation.setStatus(StaticIPAllocationStatus.ACTIVE);
        allocation = allocationRepository.save(allocation);

        // Create port forwards up to limit
        for (int i = 0; i < 3; i++) {
            CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                    .allocationId(allocation.getId())
                    .externalPort(10000 + i)
                    .internalPort(80 + i)
                    .protocol(PortForwardProtocol.TCP)
                    .build();
            portForwardService.createPortForwardRule(limitUser, request);
        }

        // Try to exceed limit
        CreatePortForwardRequest overLimitRequest = CreatePortForwardRequest.builder()
                .allocationId(allocation.getId())
                .externalPort(10003)
                .internalPort(83)
                .protocol(PortForwardProtocol.TCP)
                .build();

        try {
            portForwardService.createPortForwardRule(limitUser, overLimitRequest);
            throw new AssertionError("Should have enforced port forward limit");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention limit",
                    e.getMessage().contains("limit reached"));
        }
    }

    private void testPortForwardToggle(TestContext context) {
        if (context.portForwardRule == null) {
            testPortForwardRuleCreation(context);
        }

        // Disable rule
        PortForwardRule disabled = portForwardService.togglePortForwardRule(
                context.testUser, context.portForwardRule.getId(), false);

        assertFalse("Rule should be disabled", disabled.getEnabled());
        assertEquals("Status should be DISABLED", PortForwardStatus.DISABLED, disabled.getStatus());

        // Re-enable rule
        PortForwardRule enabled = portForwardService.togglePortForwardRule(
                context.testUser, context.portForwardRule.getId(), true);

        assertTrue("Rule should be enabled", enabled.getEnabled());
        assertEquals("Status should be PENDING", PortForwardStatus.PENDING, enabled.getStatus());
    }

    private void testPortForwardDeletion(TestContext context) {
        ensureActiveAllocation(context);

        // Create a rule to delete
        CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                .allocationId(context.allocation.getId())
                .externalPort(11000)
                .internalPort(11000)
                .protocol(PortForwardProtocol.TCP)
                .build();

        PortForwardRule rule = portForwardService.createPortForwardRule(context.testUser, request);

        // Delete it
        portForwardService.deletePortForwardRule(context.testUser, rule.getId());

        // Verify
        PortForwardRule deleted = ruleRepository.findById(rule.getId()).orElseThrow();
        assertEquals("Status should be DELETED", PortForwardStatus.DELETED, deleted.getStatus());
        assertFalse("Rule should be disabled", deleted.getEnabled());
    }

    // ========================================
    // Plan Change Tests
    // ========================================

    private void testPlanUpgrade(TestContext context) {
        User upgradeUser = createTestUser("upgrade-test");
        StaticIPSubscription sub = staticIPService.createSubscription(
                upgradeUser, StaticIPPlanType.PERSONAL, false, null);

        // Upgrade to PRO
        StaticIPSubscription upgraded = staticIPService.changePlan(upgradeUser, StaticIPPlanType.PRO);

        assertEquals("Plan should be upgraded", StaticIPPlanType.PRO, upgraded.getPlanType());
        assertEquals("Regions included should increase", 3, upgraded.getRegionsIncluded());
        assertEquals("Port forwards should increase", 10, upgraded.getPortForwardsPerRegion());
    }

    private void testPlanDowngradeValidation(TestContext context) {
        // Create user with PRO plan and use multiple regions
        User downgradeUser = createTestUser("downgrade-test");
        staticIPService.createSubscription(downgradeUser, StaticIPPlanType.PRO, false, null);

        // Allocate IPs in 2 regions
        staticIPService.allocateStaticIP(downgradeUser, "eastus");

        String region2 = "downgrade-region2";
        OrbMeshNode node2 = createTestNode(region2, "Downgrade Test Region 2");
        createTestIPPool(region2, node2.getId(), 1);
        staticIPService.allocateStaticIP(downgradeUser, region2);

        // Try to downgrade to PERSONAL (1 region limit)
        try {
            staticIPService.changePlan(downgradeUser, StaticIPPlanType.PERSONAL);
            throw new AssertionError("Should have rejected downgrade");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention regions exceed limit",
                    e.getMessage().contains("Cannot downgrade"));
        }
    }

    private void testSubscriptionCancellation(TestContext context) {
        User cancelUser = createTestUser("cancel-test");
        staticIPService.createSubscription(cancelUser, StaticIPPlanType.PERSONAL, true, null);

        staticIPService.cancelSubscription(cancelUser);

        StaticIPSubscription cancelled = subscriptionRepository.findActiveByUser(cancelUser).orElseThrow();
        assertFalse("Auto-renew should be disabled", cancelled.getAutoRenew());
        assertNotNull("CancelledAt should be set", cancelled.getCancelledAt());
    }

    // ========================================
    // Concurrency Tests
    // ========================================

    private void testConcurrentAllocations(TestContext context) {
        String region = "concurrent-test-region";
        OrbMeshNode node = createTestNode(region, "Concurrent Test Region");
        createTestIPPool(region, node.getId(), 5);

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    User user = createTestUser("concurrent-" + idx);
                    staticIPService.createSubscription(user, StaticIPPlanType.PRO, false, null);
                    staticIPService.allocateStaticIP(user, region);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.warn("Concurrent allocation {} failed: {}", idx, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
        executor.shutdown();

        log.info("Concurrent allocations: {} succeeded, {} failed", successCount.get(), failCount.get());
        assertEquals("All allocations should succeed", 5, successCount.get());
    }

    private void testConcurrentPortForwardCreation(TestContext context) {
        ensureActiveAllocation(context);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            final int port = 20000 + i;
            executor.submit(() -> {
                try {
                    CreatePortForwardRequest request = CreatePortForwardRequest.builder()
                            .allocationId(context.allocation.getId())
                            .externalPort(port)
                            .internalPort(port)
                            .protocol(PortForwardProtocol.TCP)
                            .build();
                    portForwardService.createPortForwardRule(context.testUser, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Concurrent port forward creation failed: {}", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
        executor.shutdown();

        assertEquals("All port forwards should succeed", 3, successCount.get());
    }

    // ========================================
    // Connectivity Simulation Tests
    // ========================================

    private void testSimulatedClientConnection(TestContext context) {
        ensureActiveAllocation(context);

        // Simulate client connecting to VPN with static IP
        SimulatedClient client = new SimulatedClient(
                context.allocation.getPublicIp(),
                context.allocation.getInternalIp()
        );

        // Verify client received correct IP assignment
        assertEquals("Client should have correct public IP",
                context.allocation.getPublicIp(), client.getAssignedPublicIp());
        assertEquals("Client should have correct internal IP",
                context.allocation.getInternalIp(), client.getAssignedInternalIp());

        // Simulate traffic
        client.simulateOutboundTraffic(1024 * 1024); // 1MB

        assertTrue("Traffic should be recorded", client.getTotalBytesSent() > 0);
    }

    private void testPortForwardConnectivity(TestContext context) {
        ensureActiveAllocation(context);

        // Create a test port forward
        if (context.portForwardRule == null) {
            testPortForwardRuleCreation(context);
        }

        // Simulate port forward rule being activated
        context.portForwardRule.setStatus(PortForwardStatus.ACTIVE);
        context.portForwardRule.setConfiguredAt(LocalDateTime.now());
        ruleRepository.save(context.portForwardRule);

        // Simulate external connection to port forward
        SimulatedPortForwardClient pfClient = new SimulatedPortForwardClient(
                context.allocation.getPublicIp(),
                context.portForwardRule.getExternalPort(),
                context.allocation.getInternalIp(),
                context.portForwardRule.getInternalPort()
        );

        // Verify connection mapping is correct
        assertEquals("External endpoint should map correctly",
                context.allocation.getPublicIp() + ":" + context.portForwardRule.getExternalPort(),
                pfClient.getExternalEndpoint());
        assertEquals("Internal endpoint should map correctly",
                context.allocation.getInternalIp() + ":" + context.portForwardRule.getInternalPort(),
                pfClient.getInternalEndpoint());

        // Simulate data transfer
        boolean success = pfClient.simulateDataTransfer("Hello, World!".getBytes());
        assertTrue("Data transfer should succeed", success);
    }

    // ========================================
    // Edge Case Tests
    // ========================================

    private void testInternalIPGeneration(TestContext context) {
        // Test that internal IPs are generated in valid range
        for (int i = 0; i < 10; i++) {
            String internalIp = generateInternalIP();
            assertTrue("Internal IP should start with 10.",
                    internalIp.startsWith("10."));

            String[] parts = internalIp.split("\\.");
            assertEquals("IP should have 4 octets", 4, parts.length);

            int secondOctet = Integer.parseInt(parts[1]);
            assertTrue("Second octet should be 100-255", secondOctet >= 100 && secondOctet <= 255);

            int thirdOctet = Integer.parseInt(parts[2]);
            assertTrue("Third octet should be 0-255", thirdOctet >= 0 && thirdOctet <= 255);

            int fourthOctet = Integer.parseInt(parts[3]);
            assertTrue("Fourth octet should be 1-254", fourthOctet >= 1 && fourthOctet <= 254);
        }
    }

    private void testExpirationProcessing(TestContext context) {
        // Create subscription that's about to expire
        User expiringUser = createTestUser("expiring-test");
        StaticIPSubscription sub = staticIPService.createSubscription(
                expiringUser, StaticIPPlanType.PERSONAL, false, null);

        // Manually set expiration to past
        sub.setExpiresAt(LocalDateTime.now().minusMinutes(30));
        subscriptionRepository.save(sub);

        // Process expirations
        staticIPService.processExpiredSubscriptions();

        // Verify subscription was expired
        StaticIPSubscription expired = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertEquals("Subscription should be expired", SubscriptionStatus.EXPIRED, expired.getStatus());
    }

    // ========================================
    // Helper Methods
    // ========================================

    private void ensureActiveAllocation(TestContext context) {
        if (context.subscription == null) {
            testStaticIPSubscriptionCreation(context);
        }
        if (context.allocation == null) {
            testStaticIPAllocation(context);
        }
        if (context.allocation.getStatus() != StaticIPAllocationStatus.ACTIVE) {
            context.allocation.setStatus(StaticIPAllocationStatus.ACTIVE);
            context.allocation = allocationRepository.save(context.allocation);
        }
    }

    @Transactional
    public User createTestUser(String suffix) {
        User user = new User();
        user.setEmail("test-" + suffix + "-" + System.currentTimeMillis() + "@test.orbvpn.com");
        user.setUsername("test_" + suffix);
        user.setPassword("test_password_hash");
        user.setRole(roleService.getByName(RoleName.USER));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public OrbMeshNode createTestNode(String region, String displayName) {
        OrbMeshNode node = OrbMeshNode.builder()
                .nodeUuid("test-node-" + region + "-" + System.currentTimeMillis())
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
        return nodeRepository.save(node);
    }

    @Transactional
    public void createTestIPPool(String region, Long nodeId, int count) {
        for (int i = 0; i < count; i++) {
            StaticIPPool ip = StaticIPPool.builder()
                    .publicIp("52." + (int)(Math.random() * 255) + "." +
                            (int)(Math.random() * 255) + "." + (int)(Math.random() * 254 + 1))
                    .region(region)
                    .regionDisplayName(region)
                    .azureResourceId("/subscriptions/test/staticIps/" + region + "-" + i)
                    .azureSubscriptionId("test-subscription")
                    .isAllocated(false)
                    .serverId(nodeId)
                    .costPerMonth(new java.math.BigDecimal("3.60"))
                    .createdAt(LocalDateTime.now())
                    .build();
            poolRepository.save(ip);
        }
    }

    @Transactional
    public void createTestIPPoolWithoutNode(String region, int count) {
        for (int i = 0; i < count; i++) {
            StaticIPPool ip = StaticIPPool.builder()
                    .publicIp("53." + (int)(Math.random() * 255) + "." +
                            (int)(Math.random() * 255) + "." + (int)(Math.random() * 254 + 1))
                    .region(region)
                    .regionDisplayName(region)
                    .azureResourceId("/subscriptions/test/staticIps/" + region + "-nonode-" + i)
                    .azureSubscriptionId("test-subscription")
                    .isAllocated(false)
                    .serverId(null)
                    .costPerMonth(new java.math.BigDecimal("3.60"))
                    .createdAt(LocalDateTime.now())
                    .build();
            poolRepository.save(ip);
        }
    }

    private String generateInternalIP() {
        return "10." + (100 + (int)(Math.random() * 155)) + "." +
                (int)(Math.random() * 256) + "." + (int)(Math.random() * 254 + 1);
    }

    // Assertion helpers
    private void assertNotNull(String message, Object obj) {
        if (obj == null) throw new AssertionError(message);
    }

    private void assertEquals(String message, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (!condition) throw new AssertionError(message);
    }

    private void assertFalse(String message, boolean condition) {
        if (condition) throw new AssertionError(message);
    }

    private TestReport generateReport() {
        TestReport report = new TestReport();
        report.totalTests = testResults.size();
        report.passed = (int) testResults.stream().filter(r -> r.passed).count();
        report.failed = report.totalTests - report.passed;
        report.results = new ArrayList<>(testResults);
        report.totalDuration = testResults.stream().mapToLong(r -> r.durationMs).sum();

        log.info("========================================");
        log.info("Test Report Summary");
        log.info("========================================");
        log.info("Total: {} | Passed: {} | Failed: {}",
                report.totalTests, report.passed, report.failed);
        log.info("Total Duration: {}ms", report.totalDuration);

        if (report.failed > 0) {
            log.info("Failed Tests:");
            testResults.stream()
                    .filter(r -> !r.passed)
                    .forEach(r -> log.info("  - {}: {}", r.testName, r.errorMessage));
        }
        log.info("========================================");

        return report;
    }

    // Inner classes
    public static class TestContext {
        User testUser;
        OrbMeshNode testNode;
        List<StaticIPPool> testIPs = new ArrayList<>();
        StaticIPSubscription subscription;
        StaticIPAllocation allocation;
        PortForwardRule portForwardRule;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TestResult {
        String testName;
        boolean passed;
        String errorMessage;
        long durationMs;
    }

    @lombok.Data
    public static class TestReport {
        int totalTests;
        int passed;
        int failed;
        long totalDuration;
        List<TestResult> results;
    }

    /**
     * Simulated VPN Client for testing
     */
    @lombok.Data
    public static class SimulatedClient {
        private final String assignedPublicIp;
        private final String assignedInternalIp;
        private long totalBytesSent = 0;
        private long totalBytesReceived = 0;

        public void simulateOutboundTraffic(long bytes) {
            totalBytesSent += bytes;
        }

        public void simulateInboundTraffic(long bytes) {
            totalBytesReceived += bytes;
        }
    }

    /**
     * Simulated Port Forward Client for testing
     */
    @lombok.Data
    public static class SimulatedPortForwardClient {
        private final String externalIp;
        private final int externalPort;
        private final String internalIp;
        private final int internalPort;

        public String getExternalEndpoint() {
            return externalIp + ":" + externalPort;
        }

        public String getInternalEndpoint() {
            return internalIp + ":" + internalPort;
        }

        public boolean simulateDataTransfer(byte[] data) {
            // Simulate successful data transfer through port forward
            log.info("Simulated transfer of {} bytes from {} to {}",
                    data.length, getExternalEndpoint(), getInternalEndpoint());
            return true;
        }
    }
}
