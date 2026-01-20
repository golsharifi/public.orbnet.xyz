package com.orbvpn.api.staticip;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.domain.enums.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Static IP and Port Forwarding business logic.
 * Tests domain rules, validations, and entity behavior without database.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaticIPBusinessLogicTest {

    // ========================================
    // Plan Configuration Tests
    // ========================================

    @Test
    @Order(1)
    @DisplayName("PERSONAL plan should have correct limits")
    void testPersonalPlanLimits() {
        StaticIPPlanType plan = StaticIPPlanType.PERSONAL;
        assertEquals(1, plan.getRegionsIncluded());
        assertEquals(1, plan.getPortForwardsPerRegion());
    }

    @Test
    @Order(2)
    @DisplayName("PRO plan should have correct limits")
    void testProPlanLimits() {
        StaticIPPlanType plan = StaticIPPlanType.PRO;
        assertEquals(1, plan.getRegionsIncluded());
        assertEquals(2, plan.getPortForwardsPerRegion());
    }

    @Test
    @Order(3)
    @DisplayName("MULTI_REGION plan should have correct limits")
    void testMultiRegionPlanLimits() {
        StaticIPPlanType plan = StaticIPPlanType.MULTI_REGION;
        assertEquals(3, plan.getRegionsIncluded());
        assertEquals(1, plan.getPortForwardsPerRegion());
    }

    @Test
    @Order(4)
    @DisplayName("BUSINESS plan should have correct limits")
    void testBusinessPlanLimits() {
        StaticIPPlanType plan = StaticIPPlanType.BUSINESS;
        assertEquals(5, plan.getRegionsIncluded());
        assertEquals(2, plan.getPortForwardsPerRegion());
    }

    @Test
    @Order(5)
    @DisplayName("ENTERPRISE plan should have correct limits")
    void testEnterprisePlanLimits() {
        StaticIPPlanType plan = StaticIPPlanType.ENTERPRISE;
        assertEquals(10, plan.getRegionsIncluded());
        assertEquals(3, plan.getPortForwardsPerRegion());
    }

    @Test
    @Order(6)
    @DisplayName("Total port forwards calculation")
    void testTotalPortForwardsCalculation() {
        // PERSONAL: 1 region × 1 port = 1 total
        assertEquals(1, StaticIPPlanType.PERSONAL.getTotalIncludedPortForwards());
        // PRO: 1 region × 2 ports = 2 total
        assertEquals(2, StaticIPPlanType.PRO.getTotalIncludedPortForwards());
        // MULTI_REGION: 3 regions × 1 port = 3 total
        assertEquals(3, StaticIPPlanType.MULTI_REGION.getTotalIncludedPortForwards());
        // BUSINESS: 5 regions × 2 ports = 10 total
        assertEquals(10, StaticIPPlanType.BUSINESS.getTotalIncludedPortForwards());
        // ENTERPRISE: 10 regions × 3 ports = 30 total
        assertEquals(30, StaticIPPlanType.ENTERPRISE.getTotalIncludedPortForwards());
    }

    // ========================================
    // Subscription Entity Tests
    // ========================================

    @Test
    @Order(10)
    @DisplayName("Subscription should track regions correctly")
    void testSubscriptionRegionTracking() {
        StaticIPSubscription sub = StaticIPSubscription.builder()
                .planType(StaticIPPlanType.PRO)
                .regionsIncluded(3)
                .regionsUsed(0)
                .build();

        // Initially should have 3 available
        assertEquals(3, sub.getRegionsIncluded() - sub.getRegionsUsed());

        // After using 1
        sub.setRegionsUsed(1);
        assertEquals(2, sub.getRegionsIncluded() - sub.getRegionsUsed());

        // After using all
        sub.setRegionsUsed(3);
        assertEquals(0, sub.getRegionsIncluded() - sub.getRegionsUsed());
    }

    @Test
    @Order(11)
    @DisplayName("Subscription can check if region limit reached")
    void testSubscriptionRegionLimitCheck() {
        StaticIPSubscription sub = StaticIPSubscription.builder()
                .planType(StaticIPPlanType.PERSONAL)
                .regionsIncluded(1)
                .regionsUsed(0)
                .build();

        assertFalse(sub.getRegionsUsed() >= sub.getRegionsIncluded());

        sub.setRegionsUsed(1);
        assertTrue(sub.getRegionsUsed() >= sub.getRegionsIncluded());
    }

    @Test
    @Order(12)
    @DisplayName("Subscription expiration check")
    void testSubscriptionExpiration() {
        StaticIPSubscription active = StaticIPSubscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        StaticIPSubscription expired = StaticIPSubscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        assertTrue(active.getExpiresAt().isAfter(LocalDateTime.now()));
        assertFalse(expired.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    // ========================================
    // Port Validation Tests
    // ========================================

    @Test
    @Order(20)
    @DisplayName("Blocked ports should be rejected")
    void testBlockedPorts() {
        Set<Integer> blockedPorts = Set.of(22, 23, 25, 53, 80, 443, 3306, 5432, 6379, 27017);

        for (int port : blockedPorts) {
            assertTrue(isBlockedPort(port), "Port " + port + " should be blocked");
        }
    }

    @Test
    @Order(21)
    @DisplayName("Valid ports should be allowed")
    void testValidPorts() {
        int[] validPorts = {1024, 8080, 8443, 3000, 5000, 9000, 10000, 65535};

        for (int port : validPorts) {
            assertFalse(isBlockedPort(port), "Port " + port + " should be allowed");
            assertTrue(isValidPortRange(port), "Port " + port + " should be in valid range");
        }
    }

    @Test
    @Order(22)
    @DisplayName("Ports below 1024 should be rejected")
    void testPrivilegedPorts() {
        for (int port = 1; port < 1024; port++) {
            assertFalse(isValidPortRange(port), "Port " + port + " should be rejected as privileged");
        }
    }

    @Test
    @Order(23)
    @DisplayName("Ports above 65535 should be rejected")
    void testOutOfRangePorts() {
        assertFalse(isValidPortRange(65536));
        assertFalse(isValidPortRange(70000));
        assertFalse(isValidPortRange(100000));
    }

    // ========================================
    // Internal IP Generation Tests
    // ========================================

    @Test
    @Order(30)
    @DisplayName("Internal IPs should be in correct range")
    void testInternalIPFormat() {
        for (int i = 0; i < 100; i++) {
            String ip = generateInternalIP();
            assertTrue(ip.startsWith("10."), "IP should start with 10.");

            String[] octets = ip.split("\\.");
            assertEquals(4, octets.length);

            int second = Integer.parseInt(octets[1]);
            assertTrue(second >= 100 && second <= 255);

            int third = Integer.parseInt(octets[2]);
            assertTrue(third >= 0 && third <= 255);

            int fourth = Integer.parseInt(octets[3]);
            assertTrue(fourth >= 1 && fourth <= 254);
        }
    }

    @Test
    @Order(31)
    @DisplayName("Generated IPs should be unique")
    void testInternalIPUniqueness() {
        Set<String> ips = new HashSet<>();
        int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            ips.add(generateInternalIP());
        }

        // While not guaranteed to be unique due to random generation,
        // most should be unique with the large address space
        assertTrue(ips.size() > iterations * 0.8,
                "Most IPs should be unique (got " + ips.size() + " unique out of " + iterations + ")");
    }

    // ========================================
    // Allocation Status Tests
    // ========================================

    @Test
    @Order(40)
    @DisplayName("Allocation status transitions")
    void testAllocationStatusTransitions() {
        StaticIPAllocation alloc = StaticIPAllocation.builder()
                .status(StaticIPAllocationStatus.PENDING)
                .build();

        assertEquals(StaticIPAllocationStatus.PENDING, alloc.getStatus());

        alloc.setStatus(StaticIPAllocationStatus.CONFIGURING);
        assertEquals(StaticIPAllocationStatus.CONFIGURING, alloc.getStatus());

        alloc.setStatus(StaticIPAllocationStatus.ACTIVE);
        assertEquals(StaticIPAllocationStatus.ACTIVE, alloc.getStatus());

        alloc.setStatus(StaticIPAllocationStatus.RELEASED);
        assertEquals(StaticIPAllocationStatus.RELEASED, alloc.getStatus());
    }

    @Test
    @Order(41)
    @DisplayName("Port forward status transitions")
    void testPortForwardStatusTransitions() {
        PortForwardRule rule = PortForwardRule.builder()
                .status(PortForwardStatus.PENDING)
                .enabled(true)
                .build();

        assertEquals(PortForwardStatus.PENDING, rule.getStatus());

        rule.setStatus(PortForwardStatus.CONFIGURING);
        assertEquals(PortForwardStatus.CONFIGURING, rule.getStatus());

        rule.setStatus(PortForwardStatus.ACTIVE);
        assertEquals(PortForwardStatus.ACTIVE, rule.getStatus());

        // Disable
        rule.setEnabled(false);
        rule.setStatus(PortForwardStatus.DISABLED);
        assertEquals(PortForwardStatus.DISABLED, rule.getStatus());
        assertFalse(rule.getEnabled());

        // Delete
        rule.setStatus(PortForwardStatus.DELETED);
        assertEquals(PortForwardStatus.DELETED, rule.getStatus());
    }

    // ========================================
    // Protocol Tests
    // ========================================

    @Test
    @Order(50)
    @DisplayName("Protocol values")
    void testProtocolValues() {
        assertEquals("TCP", PortForwardProtocol.TCP.name());
        assertEquals("UDP", PortForwardProtocol.UDP.name());
        assertEquals("BOTH", PortForwardProtocol.BOTH.name());
    }

    // ========================================
    // Plan Downgrade Validation Tests
    // ========================================

    @Test
    @Order(60)
    @DisplayName("Can downgrade when within limits")
    void testDowngradeValidation_WithinLimits() {
        // User has PRO (3 regions) with 1 region used
        // Can downgrade to PERSONAL (1 region)
        int regionsUsed = 1;
        int targetPlanRegions = StaticIPPlanType.PERSONAL.getRegionsIncluded();

        assertTrue(regionsUsed <= targetPlanRegions);
    }

    @Test
    @Order(61)
    @DisplayName("Cannot downgrade when exceeds limits")
    void testDowngradeValidation_ExceedsLimits() {
        // User has BUSINESS (5 regions) with 3 regions used
        // Cannot downgrade to PERSONAL (1 region)
        int regionsUsed = 3;
        int targetPlanRegions = StaticIPPlanType.PERSONAL.getRegionsIncluded();

        assertFalse(regionsUsed <= targetPlanRegions);
    }

    // ========================================
    // Cost Calculation Tests
    // ========================================

    @Test
    @Order(70)
    @DisplayName("IP pool cost tracking")
    void testIPPoolCost() {
        StaticIPPool pool = StaticIPPool.builder()
                .publicIp("20.185.100.1")
                .costPerMonth(new BigDecimal("3.60"))
                .build();

        assertEquals(new BigDecimal("3.60"), pool.getCostPerMonth());
    }

    // ========================================
    // Helper Methods (Simulating Service Logic)
    // ========================================

    private boolean isBlockedPort(int port) {
        Set<Integer> blockedPorts = Set.of(
                22,    // SSH
                23,    // Telnet
                25,    // SMTP
                53,    // DNS
                80,    // HTTP (commonly blocked for security)
                443,   // HTTPS (commonly blocked)
                3306,  // MySQL
                5432,  // PostgreSQL
                6379,  // Redis
                27017  // MongoDB
        );
        return blockedPorts.contains(port);
    }

    private boolean isValidPortRange(int port) {
        return port >= 1024 && port <= 65535;
    }

    private String generateInternalIP() {
        Random random = new Random();
        return String.format("10.%d.%d.%d",
                100 + random.nextInt(156),  // 100-255
                random.nextInt(256),         // 0-255
                1 + random.nextInt(254)      // 1-254
        );
    }
}
