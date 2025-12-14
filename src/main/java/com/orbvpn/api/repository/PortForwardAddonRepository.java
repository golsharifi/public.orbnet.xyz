package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.PortForwardAddon;
import com.orbvpn.api.domain.entity.StaticIPAllocation;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PortForwardAddonRepository extends JpaRepository<PortForwardAddon, Long> {

    // Find by user
    List<PortForwardAddon> findByUser(User user);

    // Find active addons for user
    @Query("SELECT a FROM PortForwardAddon a WHERE a.user = :user AND a.status = 'ACTIVE' " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > CURRENT_TIMESTAMP)")
    List<PortForwardAddon> findActiveByUser(@Param("user") User user);

    // Find by allocation
    List<PortForwardAddon> findByAllocation(StaticIPAllocation allocation);

    // Find active addons for allocation
    @Query("SELECT a FROM PortForwardAddon a WHERE a.allocation = :allocation AND a.status = 'ACTIVE' " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > CURRENT_TIMESTAMP)")
    List<PortForwardAddon> findActiveByAllocation(@Param("allocation") StaticIPAllocation allocation);

    // Sum available ports for allocation
    @Query("SELECT COALESCE(SUM(a.extraPorts - a.portsUsed), 0) FROM PortForwardAddon a " +
           "WHERE a.allocation = :allocation AND a.status = 'ACTIVE' " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > CURRENT_TIMESTAMP)")
    int sumAvailablePortsByAllocation(@Param("allocation") StaticIPAllocation allocation);

    // Find expiring addons
    @Query("SELECT a FROM PortForwardAddon a WHERE a.status = 'ACTIVE' " +
           "AND a.expiresAt BETWEEN :start AND :end")
    List<PortForwardAddon> findExpiringBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Find by external subscription ID
    Optional<PortForwardAddon> findByExternalSubscriptionId(String externalSubscriptionId);

    // Count by status
    long countByStatus(SubscriptionStatus status);

    // Get total revenue
    @Query("SELECT SUM(a.priceMonthly) FROM PortForwardAddon a WHERE a.status = 'ACTIVE'")
    java.math.BigDecimal getTotalMonthlyRevenue();

    // Find addon with available slots
    @Query("SELECT a FROM PortForwardAddon a WHERE a.allocation = :allocation " +
           "AND a.status = 'ACTIVE' AND a.portsUsed < a.extraPorts " +
           "AND (a.expiresAt IS NULL OR a.expiresAt > CURRENT_TIMESTAMP) " +
           "ORDER BY a.createdAt ASC")
    List<PortForwardAddon> findWithAvailableSlots(@Param("allocation") StaticIPAllocation allocation);
}
