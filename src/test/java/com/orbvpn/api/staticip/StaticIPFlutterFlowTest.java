package com.orbvpn.api.staticip;

import com.orbvpn.api.domain.dto.staticip.*;
import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.staticip.AzureStaticIPProvisioningService;
import com.orbvpn.api.service.staticip.StaticIPService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies the EXACT same flow your Flutter app will use.
 *
 * This test simulates:
 * 1. User opens Static IP screen → queries plans and regions
 * 2. User purchases a subscription → createSubscription
 * 3. User allocates IP in a region → allocateStaticIP
 * 4. User views their dashboard → getUserSubscription, getUserAllocations
 * 5. User releases an IP → releaseStaticIP
 *
 * Each test method corresponds to a GraphQL call from Flutter.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaticIPFlutterFlowTest {

    @Autowired
    private StaticIPService staticIPService;

    @Autowired
    private AzureStaticIPProvisioningService azureProvisioningService;

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

    // Test state - simulating user session
    private User testUser;
    private OrbMeshNode testNode;
    private StaticIPPool testPoolEntry;
    private String testRegion;

    // Tracking data from Flutter operations
    private StaticIPSubscription createdSubscription;
    private StaticIPAllocation allocatedIP;

    @BeforeAll
    void setUp() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("FLUTTER FLOW INTEGRATION TEST");
        System.out.println("Simulates exact API calls from Flutter app");
        System.out.println("=".repeat(60));

        // Create unique test region to avoid conflicts
        testRegion = "flutter-test-" + (System.currentTimeMillis() % 100000);

        // Simulate: User already exists in the system
        testUser = createTestUser();
        System.out.println("\n[Setup] User created: " + testUser.getEmail());

        // Create infrastructure for the test region
        testNode = createTestNode();
        System.out.println("[Setup] Node created in region: " + testRegion);

        // Create IP in pool (simulating pre-provisioned IP or Azure on-demand)
        testPoolEntry = createTestPoolEntry();
        System.out.println("[Setup] IP available in pool: " + testPoolEntry.getPublicIp());

        System.out.println("\n" + "-".repeat(60));
    }

    @AfterAll
    void tearDown() {
        System.out.println("\n" + "-".repeat(60));
        System.out.println("[Cleanup] Removing test data...");

        try {
            // Clean allocations first (due to FK constraints)
            if (testUser != null) {
                allocationRepository.findByUser(testUser).forEach(alloc -> {
                    try {
                        // Return IP to pool
                        poolRepository.findByPublicIp(alloc.getPublicIp()).ifPresent(pool -> {
                            pool.setIsAllocated(false);
                            poolRepository.save(pool);
                        });
                        allocationRepository.delete(alloc);
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                });

                // Clean subscriptions
                subscriptionRepository.findActiveByUser(testUser).ifPresent(sub -> {
                    try {
                        subscriptionRepository.delete(sub);
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                });
            }

            // Clean pool entry
            if (testPoolEntry != null) {
                try {
                    poolRepository.findById(testPoolEntry.getId()).ifPresent(poolRepository::delete);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }

            // Clean node
            if (testNode != null) {
                try {
                    nodeRepository.delete(testNode);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }

            // Clean user
            if (testUser != null) {
                try {
                    userRepository.delete(testUser);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }

            System.out.println("[Cleanup] Complete");
        } catch (Exception e) {
            System.out.println("[Cleanup] Warning: " + e.getMessage());
        }

        System.out.println("=".repeat(60) + "\n");
    }

    // ========================================
    // STEP 1: User opens Static IP screen
    // Flutter calls: staticIPPlans query
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Flutter: query staticIPPlans")
    void step1_queryPlans() {
        System.out.println("\n[Step 1] Flutter queries available plans");
        System.out.println("         GraphQL: query { staticIPPlans { ... } }");

        // This is what StaticIPQueryResolver.staticIPPlans() returns
        List<StaticIPPlanDTO> plans = staticIPService.getPlans();

        assertNotNull(plans, "Plans should not be null");
        assertFalse(plans.isEmpty(), "Should have at least one plan");

        System.out.println("         Response: " + plans.size() + " plans available");
        for (StaticIPPlanDTO plan : plans) {
            System.out.println("           - " + plan.getPlanType() + ": $" + plan.getPriceMonthly() + "/mo, "
                    + plan.getRegionsIncluded() + " regions");
        }

        // Verify plan structure matches Flutter expectations
        StaticIPPlanDTO firstPlan = plans.get(0);
        assertNotNull(firstPlan.getPlanType(), "Plan type required");
        assertNotNull(firstPlan.getName(), "Plan name required");
        assertNotNull(firstPlan.getPriceMonthly(), "Price required");
        assertTrue(firstPlan.getRegionsIncluded() > 0, "Should include regions");

        System.out.println("         ✓ Plans match Flutter expected format");
    }

    // ========================================
    // STEP 2: User views available regions
    // Flutter calls: staticIPRegions query
    // ========================================

    @Test
    @Order(2)
    @DisplayName("Flutter: query staticIPRegions")
    void step2_queryRegions() {
        System.out.println("\n[Step 2] Flutter queries available regions");
        System.out.println("         GraphQL: query { staticIPRegions { ... } }");

        // This is what StaticIPQueryResolver.staticIPRegions() returns
        List<RegionAvailabilityDTO> regions = staticIPService.getAvailableRegions();

        assertNotNull(regions, "Regions should not be null");
        assertFalse(regions.isEmpty(), "Should have at least one region");

        System.out.println("         Response: " + regions.size() + " regions available");

        // Find our test region
        RegionAvailabilityDTO ourRegion = null;
        for (RegionAvailabilityDTO region : regions) {
            if (region.getRegion().equals(testRegion)) {
                ourRegion = region;
            }
            if (region.getDisplayName() != null) {
                System.out.println("           - " + region.getRegion() + " (" + region.getDisplayName() + "): "
                        + region.getAvailableCount() + " IPs, capacity=" + region.isHasCapacity());
            }
        }

        assertNotNull(ourRegion, "Our test region should be visible");
        assertTrue(ourRegion.getAvailableCount() > 0 || ourRegion.isHasCapacity(),
                "Region should have capacity");

        System.out.println("         ✓ Regions match Flutter expected format");
        System.out.println("         ✓ Test region '" + testRegion + "' is visible with "
                + ourRegion.getAvailableCount() + " IPs");
    }

    // ========================================
    // STEP 3: User purchases subscription
    // Flutter calls: createStaticIPSubscription mutation
    // ========================================

    @Test
    @Order(10)
    @DisplayName("Flutter: mutation createStaticIPSubscription")
    void step3_createSubscription() {
        System.out.println("\n[Step 10] Flutter creates subscription");
        System.out.println("          GraphQL: mutation { createStaticIPSubscription(input: {...}) { ... } }");

        // Input from Flutter
        StaticIPPlanType selectedPlan = StaticIPPlanType.MULTI_REGION;
        boolean autoRenew = true;
        String externalId = "flutter-test-" + System.currentTimeMillis();

        System.out.println("          Input: plan=" + selectedPlan + ", autoRenew=" + autoRenew);

        // This is what StaticIPMutationResolver.createStaticIPSubscription() calls
        createdSubscription = staticIPService.createSubscription(
                testUser, selectedPlan, autoRenew, externalId);

        // Verify response
        assertNotNull(createdSubscription, "Subscription should be created");
        assertNotNull(createdSubscription.getId(), "Should have ID");
        assertEquals(selectedPlan, createdSubscription.getPlanType(), "Plan type should match");
        assertEquals(SubscriptionStatus.ACTIVE, createdSubscription.getStatus(), "Should be active");
        assertEquals(autoRenew, createdSubscription.getAutoRenew(), "AutoRenew should match");
        assertTrue(createdSubscription.getRegionsIncluded() > 0, "Should have region quota");
        assertEquals(0, createdSubscription.getRegionsUsed(), "Should start with 0 regions used");

        System.out.println("          Response: subscription ID=" + createdSubscription.getId());
        System.out.println("            - Status: " + createdSubscription.getStatus());
        System.out.println("            - Regions: " + createdSubscription.getRegionsUsed() + "/"
                + createdSubscription.getRegionsIncluded());
        System.out.println("          ✓ Subscription created successfully");
    }

    // ========================================
    // STEP 4: User allocates IP in region
    // Flutter calls: allocateStaticIP mutation
    // ========================================

    @Test
    @Order(20)
    @DisplayName("Flutter: mutation allocateStaticIP")
    void step4_allocateStaticIP() {
        System.out.println("\n[Step 20] Flutter allocates static IP");
        System.out.println("          GraphQL: mutation { allocateStaticIP(input: {region: \"" + testRegion + "\"}) { ... } }");

        assertNotNull(createdSubscription, "Must have subscription from previous test");

        // This is what StaticIPMutationResolver.allocateStaticIP() calls
        allocatedIP = staticIPService.allocateStaticIP(testUser, testRegion);

        // Verify response
        assertNotNull(allocatedIP, "Allocation should be created");
        assertNotNull(allocatedIP.getId(), "Should have ID");
        assertNotNull(allocatedIP.getPublicIp(), "Should have public IP");
        assertEquals(testRegion, allocatedIP.getRegion(), "Region should match");
        assertEquals(testPoolEntry.getPublicIp(), allocatedIP.getPublicIp(), "Should get our test IP");

        System.out.println("          Response: allocation ID=" + allocatedIP.getId());
        System.out.println("            - Public IP: " + allocatedIP.getPublicIp());
        System.out.println("            - Region: " + allocatedIP.getRegion());
        System.out.println("            - Status: " + allocatedIP.getStatus());
        System.out.println("          ✓ Static IP allocated successfully");

        // Verify subscription region count updated
        StaticIPSubscription updatedSub = subscriptionRepository.findById(createdSubscription.getId()).orElse(null);
        assertNotNull(updatedSub);
        assertEquals(1, updatedSub.getRegionsUsed(), "Regions used should be 1");
        System.out.println("          ✓ Subscription regions used updated to 1");
    }

    // ========================================
    // STEP 5: User views dashboard
    // Flutter calls: staticIPDashboard query
    // ========================================

    @Test
    @Order(30)
    @DisplayName("Flutter: query staticIPDashboard")
    void step5_queryDashboard() {
        System.out.println("\n[Step 30] Flutter queries dashboard");
        System.out.println("          GraphQL: query { staticIPDashboard { subscription {...} allocations {...} } }");

        // These are what StaticIPQueryResolver.staticIPDashboard() returns
        Optional<StaticIPSubscription> subscription = staticIPService.getUserSubscription(testUser);
        List<StaticIPAllocation> allocations = staticIPService.getUserAllocations(testUser);

        // Verify subscription
        assertTrue(subscription.isPresent(), "Should have subscription");
        assertEquals(createdSubscription.getId(), subscription.get().getId(), "Should be same subscription");

        System.out.println("          Response.subscription:");
        System.out.println("            - ID: " + subscription.get().getId());
        System.out.println("            - Plan: " + subscription.get().getPlanType());
        System.out.println("            - Regions used: " + subscription.get().getRegionsUsed());

        // Verify allocations
        assertFalse(allocations.isEmpty(), "Should have allocations");

        System.out.println("          Response.allocations: " + allocations.size() + " allocation(s)");
        for (StaticIPAllocation alloc : allocations) {
            System.out.println("            - " + alloc.getPublicIp() + " in " + alloc.getRegion()
                    + " (status: " + alloc.getStatus() + ")");
        }

        // Find our allocation
        StaticIPAllocation ourAlloc = allocations.stream()
                .filter(a -> a.getId().equals(allocatedIP.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(ourAlloc, "Should find our allocation");

        System.out.println("          ✓ Dashboard returns correct data");
    }

    // ========================================
    // STEP 6: User releases IP
    // Flutter calls: releaseStaticIP mutation
    // ========================================

    @Test
    @Order(40)
    @DisplayName("Flutter: mutation releaseStaticIP")
    void step6_releaseStaticIP() {
        System.out.println("\n[Step 40] Flutter releases static IP");
        System.out.println("          GraphQL: mutation { releaseStaticIP(allocationId: " + allocatedIP.getId() + ") }");

        assertNotNull(allocatedIP, "Must have allocation from previous test");

        // This is what StaticIPMutationResolver.releaseStaticIP() calls
        staticIPService.releaseStaticIP(testUser, allocatedIP.getId());

        System.out.println("          ✓ Release completed");

        // Verify allocation status
        StaticIPAllocation released = allocationRepository.findById(allocatedIP.getId()).orElse(null);
        assertNotNull(released);
        assertEquals(StaticIPAllocationStatus.RELEASED, released.getStatus(), "Should be RELEASED");
        assertNotNull(released.getReleasedAt(), "Should have release timestamp");

        System.out.println("          ✓ Allocation status: " + released.getStatus());

        // Verify subscription region count decremented
        StaticIPSubscription updatedSub = subscriptionRepository.findById(createdSubscription.getId()).orElse(null);
        assertNotNull(updatedSub);
        assertEquals(0, updatedSub.getRegionsUsed(), "Regions used should be 0");
        System.out.println("          ✓ Subscription regions used decremented to 0");

        // Verify IP returned to pool
        StaticIPPool poolEntry = poolRepository.findByPublicIp(allocatedIP.getPublicIp()).orElse(null);
        assertNotNull(poolEntry);
        assertFalse(poolEntry.getIsAllocated(), "IP should be available in pool");
        System.out.println("          ✓ IP returned to pool and available");
    }

    // ========================================
    // STEP 7: User checks dashboard after release
    // Flutter calls: myStaticIPAllocations query
    // ========================================

    @Test
    @Order(50)
    @DisplayName("Flutter: verify dashboard after release")
    void step7_verifyAfterRelease() {
        System.out.println("\n[Step 50] Flutter verifies dashboard after release");
        System.out.println("          GraphQL: query { myStaticIPAllocations { ... } }");

        // Active allocations should be empty
        List<StaticIPAllocation> activeAllocations = staticIPService.getUserAllocations(testUser);

        System.out.println("          Active allocations: " + activeAllocations.size());

        // The getUserAllocations returns ACTIVE allocations only
        assertTrue(activeAllocations.isEmpty() ||
                   activeAllocations.stream().noneMatch(a -> a.getId().equals(allocatedIP.getId())),
                "Released allocation should not appear in active list");

        System.out.println("          ✓ Dashboard correctly shows no active allocations");
    }

    // ========================================
    // ERROR CASE: Allocate without subscription
    // ========================================

    @Test
    @Order(100)
    @DisplayName("Flutter: error case - allocate without subscription")
    void errorCase_allocateWithoutSubscription() {
        System.out.println("\n[Error Case] Try allocating without subscription");

        // Create a new user without subscription
        User noSubUser = new User();
        noSubUser.setEmail("no-sub-user@test.orbvpn.com");
        noSubUser.setUsername("no_sub_user_" + System.currentTimeMillis());
        noSubUser.setPassword("$2a$10$test");
        noSubUser.setRole(roleService.getByName(RoleName.USER));
        noSubUser.setEnabled(true);
        noSubUser.setCreatedAt(LocalDateTime.now());
        noSubUser = userRepository.save(noSubUser);

        final User finalUser = noSubUser;

        try {
            Exception exception = assertThrows(IllegalStateException.class, () -> {
                staticIPService.allocateStaticIP(finalUser, testRegion);
            });

            System.out.println("          Error message: " + exception.getMessage());
            assertTrue(exception.getMessage().contains("subscription"),
                    "Error should mention subscription");
            System.out.println("          ✓ Correct error returned to Flutter");
        } finally {
            userRepository.delete(noSubUser);
        }
    }

    // ========================================
    // ERROR CASE: Duplicate subscription
    // ========================================

    @Test
    @Order(101)
    @DisplayName("Flutter: error case - duplicate subscription")
    void errorCase_duplicateSubscription() {
        System.out.println("\n[Error Case] Try creating duplicate subscription");

        // User already has subscription from step 3
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            staticIPService.createSubscription(testUser, StaticIPPlanType.PERSONAL, false, "dup-test");
        });

        System.out.println("          Error message: " + exception.getMessage());
        assertTrue(exception.getMessage().toLowerCase().contains("already"),
                "Error should indicate existing subscription");
        System.out.println("          ✓ Correct error returned to Flutter");
    }

    // ========================================
    // Helper Methods
    // ========================================

    private User createTestUser() {
        User user = new User();
        user.setEmail("flutter-test-" + System.currentTimeMillis() + "@test.orbvpn.com");
        user.setUsername("flutter_test_" + System.currentTimeMillis());
        user.setPassword("$2a$10$test_password_hash");
        user.setRole(roleService.getByName(RoleName.USER));
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private OrbMeshNode createTestNode() {
        String nodeUuid = "flutter-" + (System.currentTimeMillis() % 1000000);
        OrbMeshNode node = OrbMeshNode.builder()
                .nodeUuid(nodeUuid)
                .deploymentType(DeploymentType.ORBVPN_DC)
                .region(testRegion)
                .regionDisplayName("Flutter Test Region")
                .publicIp("10.88.88." + (int)(Math.random() * 254 + 1))
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
        return nodeRepository.save(node);
    }

    private StaticIPPool createTestPoolEntry() {
        String uniqueIp = "10.100." + (int)(Math.random() * 255) + "." + (int)(Math.random() * 254 + 1);

        StaticIPPool pool = StaticIPPool.builder()
                .publicIp(uniqueIp)
                .region(testRegion)
                .regionDisplayName("Flutter Test Region")
                .azureResourceId("flutter-test-" + System.currentTimeMillis())
                .azureSubscriptionId("flutter-test-subscription")
                .serverId(testNode.getId())
                .isAllocated(false)
                .costPerMonth(new BigDecimal("3.60"))
                .build();
        return poolRepository.save(pool);
    }
}
