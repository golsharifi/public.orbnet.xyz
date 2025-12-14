package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.PortForwardRule;
import com.orbvpn.api.domain.entity.StaticIPAllocation;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.PortForwardProtocol;
import com.orbvpn.api.domain.enums.PortForwardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortForwardRuleRepository extends JpaRepository<PortForwardRule, Long> {

    // Find by user
    List<PortForwardRule> findByUser(User user);

    // Find by allocation
    List<PortForwardRule> findByAllocation(StaticIPAllocation allocation);

    // Find active rules by allocation
    @Query("SELECT r FROM PortForwardRule r WHERE r.allocation = :allocation AND r.status = 'ACTIVE'")
    List<PortForwardRule> findActiveByAllocation(@Param("allocation") StaticIPAllocation allocation);

    // Find by allocation and external port (check for conflicts)
    Optional<PortForwardRule> findByAllocationAndExternalPortAndProtocol(
            StaticIPAllocation allocation, Integer externalPort, PortForwardProtocol protocol);

    // Count rules per allocation (to check limits)
    @Query("SELECT COUNT(r) FROM PortForwardRule r WHERE r.allocation = :allocation " +
           "AND r.status != 'DELETED' AND r.isFromAddon = false")
    int countIncludedByAllocation(@Param("allocation") StaticIPAllocation allocation);

    // Count addon rules per allocation
    @Query("SELECT COUNT(r) FROM PortForwardRule r WHERE r.allocation = :allocation " +
           "AND r.status != 'DELETED' AND r.isFromAddon = true")
    int countAddonByAllocation(@Param("allocation") StaticIPAllocation allocation);

    // Find rules needing configuration
    @Query("SELECT r FROM PortForwardRule r WHERE r.status = 'PENDING' OR r.status = 'CONFIGURING'")
    List<PortForwardRule> findNeedingConfiguration();

    // Find enabled rules for a server
    @Query("SELECT r FROM PortForwardRule r WHERE r.allocation.serverId = :serverId " +
           "AND r.status = 'ACTIVE' AND r.enabled = true")
    List<PortForwardRule> findActiveByServerId(@Param("serverId") Long serverId);

    // Delete all rules for an allocation
    @Modifying
    @Query("UPDATE PortForwardRule r SET r.status = 'DELETED' WHERE r.allocation = :allocation")
    int softDeleteByAllocation(@Param("allocation") StaticIPAllocation allocation);

    // Check if port is blocked
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM PortForwardRule r " +
           "WHERE r.allocation = :allocation AND r.externalPort = :port AND r.status != 'DELETED'")
    boolean isPortUsed(@Param("allocation") StaticIPAllocation allocation, @Param("port") Integer port);

    // Get total rules count for user
    @Query("SELECT COUNT(r) FROM PortForwardRule r WHERE r.user = :user AND r.status != 'DELETED'")
    long countByUser(@Param("user") User user);

    // Find rules with errors
    @Query("SELECT r FROM PortForwardRule r WHERE r.status = 'ERROR'")
    List<PortForwardRule> findWithErrors();
}
