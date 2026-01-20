package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.StaticIPAllocation;
import com.orbvpn.api.domain.entity.StaticIPSubscription;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.StaticIPAllocationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaticIPAllocationRepository extends JpaRepository<StaticIPAllocation, Long> {

    // Find by user and region
    Optional<StaticIPAllocation> findByUserAndRegionAndStatus(
            User user, String region, StaticIPAllocationStatus status);

    // Find all allocations for user
    List<StaticIPAllocation> findByUser(User user);

    // Find active allocations for user (includes PENDING, CONFIGURING, and ACTIVE - excludes RELEASED/SUSPENDED)
    @Query("SELECT a FROM StaticIPAllocation a WHERE a.user = :user AND a.status IN ('PENDING', 'CONFIGURING', 'ACTIVE')")
    List<StaticIPAllocation> findActiveByUser(@Param("user") User user);

    // Find by public IP
    Optional<StaticIPAllocation> findByPublicIp(String publicIp);

    // Check if public IP exists
    boolean existsByPublicIp(String publicIp);

    // Find by subscription
    List<StaticIPAllocation> findBySubscription(StaticIPSubscription subscription);

    // Find allocations needing NAT configuration
    @Query("SELECT a FROM StaticIPAllocation a WHERE a.status = 'PENDING' OR a.status = 'CONFIGURING'")
    List<StaticIPAllocation> findNeedingConfiguration();

    // Find by user ID with lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM StaticIPAllocation a WHERE a.user.id = :userId")
    List<StaticIPAllocation> findByUserIdWithLock(@Param("userId") int userId);

    // Count allocations per region
    @Query("SELECT a.region, COUNT(a) FROM StaticIPAllocation a WHERE a.status = 'ACTIVE' GROUP BY a.region")
    List<Object[]> countActiveByRegion();

    // Count user's allocations
    @Query("SELECT COUNT(a) FROM StaticIPAllocation a WHERE a.user = :user AND a.status = 'ACTIVE'")
    int countActiveByUser(@Param("user") User user);

    // Find by server ID (for NAT cleanup on server shutdown)
    List<StaticIPAllocation> findByServerId(Long serverId);

    // Find by internal IP
    Optional<StaticIPAllocation> findByInternalIp(String internalIp);

    // Find all active allocations (for heartbeat checks)
    @Query("SELECT a FROM StaticIPAllocation a WHERE a.status = 'ACTIVE'")
    List<StaticIPAllocation> findAllActive();
}
