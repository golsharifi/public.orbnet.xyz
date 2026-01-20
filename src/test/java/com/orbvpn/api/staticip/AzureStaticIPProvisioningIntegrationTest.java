package com.orbvpn.api.staticip;

import com.orbvpn.api.config.AzureStaticIPProperties;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.staticip.AzureStaticIPProvisioningService;
import com.orbvpn.api.service.staticip.StaticIPService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Azure Static IP Provisioning.
 *
 * These tests verify:
 * 1. Real database integration (using actual database connection)
 * 2. Azure SDK integration for dynamic IP provisioning
 * 3. Multiple IP allocation scenarios
 * 4. Concurrent provisioning safety
 *
 * PREREQUISITES:
 * - Azure credentials must be configured (environment variables or application properties)
 * - Database must be accessible
 *
 * CONFIGURATION:
 * Set the following environment variables or application properties:
 * - AZURE_STATICIP_PROVISIONING_ENABLED=true
 * - AZURE_STATICIP_SUBSCRIPTION_ID=your-subscription-id
 * - AZURE_STATICIP_TENANT_ID=your-tenant-id
 * - AZURE_STATICIP_CLIENT_ID=your-client-id
 * - AZURE_STATICIP_CLIENT_SECRET=your-client-secret
 * - AZURE_STATICIP_RESOURCE_GROUP=your-resource-group
 *
 * To run: mvn test -Dtest=AzureStaticIPProvisioningIntegrationTest
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AzureStaticIPProvisioningIntegrationTest {

    @Autowired
    private StaticIPService staticIPService;

    @Autowired
    private AzureStaticIPProvisioningService azureProvisioningService;

    @Autowired
    private AzureStaticIPProperties azureProperties;

    @Autowired
    private StaticIPPoolRepository poolRepository;

    @Autowired
    private StaticIPSubscriptionRepository subscriptionRepository;

    @Autowired
    private StaticIPAllocationRepository allocationRepository;

    @Autowired
    private OrbMeshNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleService roleService;

    // Test data tracking for cleanup
    private final List<StaticIPPool> provisionedIPs = Collections.synchronizedList(new ArrayList<>());
    private final List<User> testUsers = new ArrayList<>();
    private final List<OrbMeshNode> testNodes = new ArrayList<>();

    // Test fixtures
    private User testUser;
    private OrbMeshNode testNode;
    // Use real Azure region for Azure tests, unique test region for non-Azure tests
    private static final String AZURE_TEST_REGION = "eastus";
    private static final String TEST_EMAIL_PREFIX = "azure-staticip-test-";
    private static final String TEST_NODE_PREFIX = "azure-test-node-";

    // Dynamic test region for non-Azure tests to avoid data conflicts
    private String testRegion;
    private boolean azureEnabled;

    @BeforeAll
    void setUp() {
        System.out.println("========================================");
        System.out.println("Azure Static IP Provisioning Integration Tests");
        System.out.println("========================================");

        azureEnabled = azureProvisioningService.isProvisioningAvailable();

        System.out.println("\nAzure Configuration:");
        System.out.println("  Provisioning Enabled: " + azureProperties.isProvisioningEnabled());
        System.out.println("  Subscription ID: " + maskString(azureProperties.getSubscriptionId()));
        System.out.println("  Resource Group: " + azureProperties.getResourceGroup());
        System.out.println("  SDK Available: " + azureEnabled);

        if (!azureEnabled) {
            System.out.println("\n⚠️  Azure provisioning is NOT available.");
            System.out.println("    Tests will run in simulation mode (pool-only).");
            System.out.println("    To enable Azure tests, configure Azure credentials.");
        }

        // Set test region based on Azure availability
        // Use unique region for non-Azure tests to avoid conflicts with existing data
        testRegion = azureEnabled ? AZURE_TEST_REGION : "testrgn" + (System.currentTimeMillis() % 100000);
        System.out.println("\nUsing test region: " + testRegion);

        // Create test user
        testUser = createTestUser("main");
        System.out.println("Created test user: " + testUser.getEmail());

        // Create test node for the region
        testNode = createTestNode(testRegion, "Test Region");
        System.out.println("Created test node: " + testNode.getNodeUuid());

        System.out.println("\nSetup completed");
        System.out.println("========================================\n");
    }

    @AfterAll
    void tearDown() {
        System.out.println("\n========================================");
        System.out.println("Cleaning up test resources...");
        System.out.println("========================================");

        // Clean up provisioned IPs from Azure
        if (azureEnabled) {
            cleanupAzureResources();
        }

        // Clean up database test data
        cleanupDatabaseResources();

        System.out.println("Cleanup complete");
    }

    // ========================================
    // Azure Provisioning Tests
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Azure SDK initialization check")
    void testAzureInitialization() {
        System.out.println("\n--- Test: Azure SDK Initialization ---");

        if (azureEnabled) {
            assertTrue(azureProvisioningService.isProvisioningAvailable(),
                    "Azure provisioning should be available when configured");
            System.out.println("✓ Azure SDK initialized successfully");
        } else {
            assertFalse(azureProvisioningService.isProvisioningAvailable(),
                    "Azure provisioning should not be available without configuration");
            System.out.println("✓ Azure SDK correctly reports unavailable (not configured)");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Get available Azure regions")
    void testGetAvailableRegions() {
        System.out.println("\n--- Test: Available Azure Regions ---");

        List<String> regions = azureProvisioningService.getAvailableRegions();

        assertNotNull(regions, "Regions list should not be null");
        assertFalse(regions.isEmpty(), "Should have at least one region");

        System.out.println("Available regions:");
        for (String region : regions) {
            String displayName = azureProvisioningService.getRegionDisplayName(region);
            System.out.println("  - " + region + " (" + displayName + ")");
        }
    }

    @Test
    @Order(10)
    @DisplayName("Provision single static IP from Azure")
    void testProvisionSingleStaticIP() {
        Assumptions.assumeTrue(azureEnabled,
                "Skipping Azure provisioning test - Azure not configured");

        System.out.println("\n--- Test: Provision Single Static IP ---");

        StaticIPPool provisionedIP = azureProvisioningService.provisionStaticIP(AZURE_TEST_REGION, testNode.getId());
        provisionedIPs.add(provisionedIP);

        assertNotNull(provisionedIP, "Provisioned IP should not be null");
        assertNotNull(provisionedIP.getPublicIp(), "Public IP should be assigned");
        assertNotNull(provisionedIP.getAzureResourceId(), "Azure resource ID should be set");
        assertEquals(AZURE_TEST_REGION, provisionedIP.getRegion());
        assertFalse(provisionedIP.getIsAllocated(), "IP should not be allocated yet");

        System.out.println("✓ Provisioned static IP:");
        System.out.println("  Public IP: " + provisionedIP.getPublicIp());
        System.out.println("  Azure Resource: " + provisionedIP.getAzureResourceId());
        System.out.println("  Region: " + provisionedIP.getRegion());
        System.out.println("  Cost/month: $" + provisionedIP.getCostPerMonth());
    }

    @Test
    @Order(11)
    @DisplayName("Verify provisioned IP in Azure")
    void testVerifyProvisionedIP() {
        Assumptions.assumeTrue(azureEnabled,
                "Skipping Azure verification test - Azure not configured");
        Assumptions.assumeFalse(provisionedIPs.isEmpty(),
                "Skipping - no IPs were provisioned");

        System.out.println("\n--- Test: Verify Provisioned IP ---");

        StaticIPPool ipToVerify = provisionedIPs.get(0);

        boolean isValid = azureProvisioningService.verifyStaticIP(ipToVerify);

        assertTrue(isValid, "Provisioned IP should be verified in Azure");
        System.out.println("✓ IP " + ipToVerify.getPublicIp() + " verified in Azure");
    }

    @Test
    @Order(20)
    @DisplayName("Provision multiple static IPs from Azure")
    void testProvisionMultipleStaticIPs() {
        Assumptions.assumeTrue(azureEnabled,
                "Skipping Azure provisioning test - Azure not configured");

        System.out.println("\n--- Test: Provision Multiple Static IPs ---");

        int countToProvision = 3;
        List<StaticIPPool> newIPs = azureProvisioningService.provisionMultipleStaticIPs(
                AZURE_TEST_REGION, countToProvision, testNode.getId());

        provisionedIPs.addAll(newIPs);

        assertNotNull(newIPs, "Provisioned IPs list should not be null");
        assertEquals(countToProvision, newIPs.size(),
                "Should have provisioned " + countToProvision + " IPs");

        // Verify all IPs are unique
        Set<String> uniqueIPs = new HashSet<>();
        for (StaticIPPool ip : newIPs) {
            assertTrue(uniqueIPs.add(ip.getPublicIp()),
                    "All provisioned IPs should be unique");
        }

        System.out.println("✓ Provisioned " + newIPs.size() + " static IPs:");
        for (StaticIPPool ip : newIPs) {
            System.out.println("  - " + ip.getPublicIp());
        }
    }

    // ========================================
    // Static IP Allocation with Azure Fallback Tests
    // ========================================

    @Test
    @Order(30)
    @DisplayName("Allocate static IP - pool available")
    void testAllocateStaticIPFromPool() {
        System.out.println("\n--- Test: Allocate Static IP from Pool ---");

        // Always use a unique region for pool allocation to avoid conflicts with existing data
        String allocationTestRegion = "pool-test-" + (System.currentTimeMillis() % 100000);

        // Create test node for this unique region
        OrbMeshNode allocationTestNode = createTestNode(allocationTestRegion, "Pool Test Region");

        // Create a test IP in the pool manually with unique address
        String uniqueTestIp = generateUniqueTestIp();
        StaticIPPool testIP = StaticIPPool.builder()
                .publicIp(uniqueTestIp)
                .region(allocationTestRegion)
                .regionDisplayName("Pool Test Region")
                .azureResourceId("test-resource-" + System.currentTimeMillis())
                .azureSubscriptionId("test-subscription")
                .serverId(allocationTestNode.getId())
                .isAllocated(false)
                .costPerMonth(new BigDecimal("3.60"))
                .build();
        testIP = poolRepository.save(testIP);
        provisionedIPs.add(testIP);

        // Create subscription for user
        StaticIPSubscription subscription = staticIPService.createSubscription(
                testUser, StaticIPPlanType.MULTI_REGION, false, "test-sub-" + System.currentTimeMillis());

        System.out.println("Created subscription: " + subscription.getId());
        System.out.println("Using test region: " + allocationTestRegion);
        System.out.println("Test IP in pool: " + uniqueTestIp);

        // Allocate IP
        StaticIPAllocation allocation = staticIPService.allocateStaticIP(testUser, allocationTestRegion);

        assertNotNull(allocation, "Allocation should not be null");
        assertNotNull(allocation.getPublicIp(), "Should have public IP");
        assertEquals(allocationTestRegion, allocation.getRegion());
        assertEquals(uniqueTestIp, allocation.getPublicIp(), "Should allocate our test IP");

        System.out.println("✓ Allocated static IP:");
        System.out.println("  Public IP: " + allocation.getPublicIp());
        System.out.println("  Internal IP: " + allocation.getInternalIp());
        System.out.println("  Status: " + allocation.getStatus());

        // Clean up allocation for next tests
        staticIPService.releaseStaticIP(testUser, allocation.getId());
        System.out.println("✓ Released allocation for cleanup");
    }

    @Test
    @Order(31)
    @DisplayName("Allocate static IP - triggers Azure provisioning when pool empty")
    void testAllocateStaticIPTriggersAzureProvisioning() {
        Assumptions.assumeTrue(azureEnabled,
                "Skipping Azure fallback test - Azure not configured");

        System.out.println("\n--- Test: Allocate with Azure Fallback ---");

        // Empty the pool for the test region
        String emptyRegion = "westus2"; // Use a different region to not affect other tests

        // Create a node for this region (needed for allocation)
        createTestNode(emptyRegion, "US West 2 (Test)");

        // Ensure no IPs available in this region
        long availableBefore = poolRepository.findAvailableByRegion(emptyRegion,
                org.springframework.data.domain.PageRequest.of(0, 100)).size();
        System.out.println("Available IPs in " + emptyRegion + " before: " + availableBefore);

        if (availableBefore == 0) {
            // No IPs in pool, allocation should trigger Azure provisioning
            User azureTestUser = createTestUser("azure-fallback");
            staticIPService.createSubscription(azureTestUser, StaticIPPlanType.PERSONAL,
                    false, "azure-test-" + System.currentTimeMillis());

            StaticIPAllocation allocation = staticIPService.allocateStaticIP(azureTestUser, emptyRegion);

            assertNotNull(allocation, "Allocation should succeed via Azure provisioning");
            assertNotNull(allocation.getPublicIp(), "Should have Azure-provisioned IP");

            System.out.println("✓ Successfully allocated via Azure provisioning:");
            System.out.println("  Public IP: " + allocation.getPublicIp());

            // Track for cleanup
            poolRepository.findByPublicIp(allocation.getPublicIp())
                    .ifPresent(provisionedIPs::add);

            // Release the allocation
            staticIPService.releaseStaticIP(azureTestUser, allocation.getId());
        } else {
            System.out.println("⚠️  Pool not empty, cannot test Azure fallback behavior");
        }
    }

    // ========================================
    // Concurrent Provisioning Tests
    // ========================================

    @Test
    @Order(40)
    @DisplayName("Concurrent static IP provisioning")
    void testConcurrentProvisioning() {
        Assumptions.assumeTrue(azureEnabled,
                "Skipping concurrent provisioning test - Azure not configured");

        System.out.println("\n--- Test: Concurrent IP Provisioning ---");

        int concurrentRequests = 3;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<StaticIPPool> concurrentIPs = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    StaticIPPool ip = azureProvisioningService.provisionStaticIP(AZURE_TEST_REGION, testNode.getId());
                    concurrentIPs.add(ip);
                    successCount.incrementAndGet();
                    System.out.println("  ✓ Provisioned: " + ip.getPublicIp());
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("  ✗ Failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        try {
            boolean completed = doneLatch.await(5, TimeUnit.MINUTES);
            assertTrue(completed, "All provisioning requests should complete within timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }

        executor.shutdown();

        provisionedIPs.addAll(concurrentIPs);

        System.out.println("\nConcurrent provisioning results:");
        System.out.println("  Successful: " + successCount.get());
        System.out.println("  Failed: " + failCount.get());

        // Verify no duplicate IPs
        Set<String> uniqueIPs = new HashSet<>();
        for (StaticIPPool ip : concurrentIPs) {
            assertTrue(uniqueIPs.add(ip.getPublicIp()),
                    "Concurrent provisioning should not produce duplicate IPs");
        }

        assertTrue(successCount.get() >= 1, "At least one concurrent request should succeed");
    }

    // ========================================
    // Deprovisioning Tests
    // ========================================

    @Test
    @Order(50)
    @DisplayName("Deprovision static IP from Azure")
    void testDeprovisionStaticIP() {
        Assumptions.assumeTrue(azureEnabled,
                "Skipping deprovision test - Azure not configured");
        Assumptions.assumeFalse(provisionedIPs.isEmpty(),
                "Skipping - no IPs to deprovision");

        System.out.println("\n--- Test: Deprovision Static IP ---");

        // Get an unallocated IP to deprovision
        StaticIPPool ipToDeprovision = null;
        for (StaticIPPool ip : provisionedIPs) {
            if (!ip.getIsAllocated()) {
                ipToDeprovision = poolRepository.findById(ip.getId()).orElse(null);
                if (ipToDeprovision != null && !ipToDeprovision.getIsAllocated()) {
                    break;
                }
            }
        }

        if (ipToDeprovision == null) {
            System.out.println("⚠️  No unallocated IPs available for deprovisioning test");
            return;
        }

        String publicIp = ipToDeprovision.getPublicIp();
        System.out.println("Deprovisioning IP: " + publicIp);

        azureProvisioningService.deprovisionStaticIP(ipToDeprovision);
        provisionedIPs.remove(ipToDeprovision);

        // Verify removed from database
        Optional<StaticIPPool> deleted = poolRepository.findByPublicIp(publicIp);
        assertTrue(deleted.isEmpty(), "IP should be removed from database");

        System.out.println("✓ Successfully deprovisioned IP: " + publicIp);
    }

    // ========================================
    // Database State Verification
    // ========================================

    @Test
    @Order(60)
    @DisplayName("Verify database state consistency")
    void testDatabaseConsistency() {
        System.out.println("\n--- Test: Database State Verification ---");

        long totalPoolIPs = poolRepository.count();
        long allocatedIPs = poolRepository.countByIsAllocatedTrue();
        long availableIPs = poolRepository.countByIsAllocatedFalse();

        System.out.println("Database Statistics:");
        System.out.println("  Total IPs in pool: " + totalPoolIPs);
        System.out.println("  Allocated: " + allocatedIPs);
        System.out.println("  Available: " + availableIPs);

        assertEquals(totalPoolIPs, allocatedIPs + availableIPs,
                "Total should equal allocated + available");

        // Verify test IPs are tracked
        System.out.println("  Test IPs tracked: " + provisionedIPs.size());

        System.out.println("✓ Database state is consistent");
    }

    // ========================================
    // Helper Methods
    // ========================================

    private User createTestUser(String suffix) {
        User user = new User();
        user.setEmail(TEST_EMAIL_PREFIX + suffix + "-" + System.currentTimeMillis() + "@test.orbvpn.com");
        user.setUsername("azure_test_" + suffix + "_" + System.currentTimeMillis());
        user.setPassword("$2a$10$test_password_hash");
        user.setRole(roleService.getByName(RoleName.USER));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user = userRepository.save(user);
        testUsers.add(user);
        return user;
    }

    private OrbMeshNode createTestNode(String region, String displayName) {
        // Keep nodeUuid under 36 chars (varchar limit)
        String shortRegion = region.length() > 10 ? region.substring(0, 10) : region;
        String nodeUuid = "test-" + shortRegion + "-" + (System.currentTimeMillis() % 1000000);
        OrbMeshNode node = OrbMeshNode.builder()
                .nodeUuid(nodeUuid)
                .deploymentType(DeploymentType.ORBVPN_DC)
                .region(region)
                .regionDisplayName(displayName)
                .publicIp("10.0.0." + (int)(Math.random() * 254 + 1))
                .isActive(true)
                .online(true)
                .supportsStaticIp(true)
                .staticIpsAvailable(100)
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

    private void cleanupAzureResources() {
        System.out.println("\nCleaning up Azure resources...");
        int cleaned = 0;
        int failed = 0;

        for (StaticIPPool ip : new ArrayList<>(provisionedIPs)) {
            try {
                // Refresh from database
                Optional<StaticIPPool> current = poolRepository.findById(ip.getId());
                if (current.isPresent() && !current.get().getIsAllocated()) {
                    azureProvisioningService.deprovisionStaticIP(current.get());
                    cleaned++;
                    System.out.println("  ✓ Deprovisioned: " + ip.getPublicIp());
                }
            } catch (Exception e) {
                failed++;
                System.out.println("  ✗ Failed to deprovision " + ip.getPublicIp() + ": " + e.getMessage());
            }
        }

        System.out.println("Azure cleanup: " + cleaned + " deprovisioned, " + failed + " failed");
    }

    private void cleanupDatabaseResources() {
        System.out.println("\nCleaning up database resources...");

        // Clean up test users and their data
        for (User user : testUsers) {
            try {
                // Delete subscriptions
                subscriptionRepository.findActiveByUser(user).ifPresent(sub -> {
                    subscriptionRepository.delete(sub);
                });

                // Delete allocations
                allocationRepository.findByUser(user).forEach(alloc -> {
                    // Return IP to pool
                    poolRepository.findByPublicIp(alloc.getPublicIp()).ifPresent(pool -> {
                        pool.setIsAllocated(false);
                        pool.setAllocatedAt(null);
                        poolRepository.save(pool);
                    });
                    allocationRepository.delete(alloc);
                });

                userRepository.delete(user);
            } catch (Exception e) {
                System.out.println("  ✗ Failed to cleanup user " + user.getEmail() + ": " + e.getMessage());
            }
        }

        // Clean up test nodes
        for (OrbMeshNode node : testNodes) {
            try {
                nodeRepository.delete(node);
            } catch (Exception e) {
                System.out.println("  ✗ Failed to cleanup node: " + e.getMessage());
            }
        }

        // Clean up any remaining test pool entries
        poolRepository.findAll().stream()
                .filter(p -> p.getAzureResourceId() != null && p.getAzureResourceId().startsWith("test-resource-"))
                .forEach(pool -> {
                    try {
                        poolRepository.delete(pool);
                    } catch (Exception e) {
                        System.out.println("  ✗ Failed to cleanup pool entry: " + e.getMessage());
                    }
                });

        System.out.println("Database cleanup complete");
    }

    private String maskString(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    /**
     * Generate a unique test IP that doesn't conflict with existing data.
     * Uses 192.168.x.x range with timestamp-based uniqueness.
     */
    private String generateUniqueTestIp() {
        // Use timestamp components for uniqueness in 192.168.x.x range
        long ts = System.currentTimeMillis();
        int octet3 = (int) ((ts / 1000) % 256);
        int octet4 = (int) (ts % 256);
        if (octet4 == 0) octet4 = 1;

        String baseIp = "192.168." + octet3 + "." + octet4;

        // Check if IP already exists and increment if necessary
        int attempts = 0;
        String testIp = baseIp;
        while (poolRepository.findByPublicIp(testIp).isPresent() ||
               allocationRepository.existsByPublicIp(testIp)) {
            attempts++;
            int newOctet4 = (octet4 + attempts) % 255;
            if (newOctet4 == 0) newOctet4 = 1;
            testIp = "192.168." + octet3 + "." + newOctet4;
            if (attempts > 250) {
                // Try a different octet3
                octet3 = (octet3 + 1) % 256;
                testIp = "192.168." + octet3 + "." + octet4;
                attempts = 0;
            }
        }

        return testIp;
    }
}
