package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.StaticIPPool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaticIPPoolRepository extends JpaRepository<StaticIPPool, Long> {

    // Find available IP in region
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StaticIPPool> findFirstByRegionAndIsAllocatedFalse(String region);

    // Find by public IP
    Optional<StaticIPPool> findByPublicIp(String publicIp);

    // Count available IPs per region
    @Query("SELECT s.region, COUNT(s) FROM StaticIPPool s WHERE s.isAllocated = false GROUP BY s.region")
    List<Object[]> countAvailableByRegion();

    // Find all IPs in a region
    List<StaticIPPool> findByRegion(String region);

    // Find allocated IPs for a user
    List<StaticIPPool> findByAllocatedToUserId(Integer userId);

    // Get distinct regions
    @Query("SELECT DISTINCT s.region FROM StaticIPPool s ORDER BY s.region")
    List<String> findDistinctRegions();

    // Get regions with available IPs
    @Query("SELECT DISTINCT s.region FROM StaticIPPool s WHERE s.isAllocated = false ORDER BY s.region")
    List<String> findRegionsWithAvailableIps();

    // Count total IPs per region
    @Query("SELECT s.region, s.regionDisplayName, " +
           "SUM(CASE WHEN s.isAllocated = false THEN 1 ELSE 0 END), " +
           "COUNT(s) " +
           "FROM StaticIPPool s GROUP BY s.region, s.regionDisplayName")
    List<Object[]> getRegionStats();

    // Find by Azure resource ID
    Optional<StaticIPPool> findByAzureResourceId(String azureResourceId);

    // Count allocated IPs
    long countByIsAllocatedTrue();

    // Count available IPs
    long countByIsAllocatedFalse();

    // Count total IPs in a region
    long countByRegion(String region);

    // Find first available IP in a region (alias for findFirstByRegionAndIsAllocatedFalse)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StaticIPPool s WHERE s.region = :region AND s.isAllocated = false ORDER BY s.id")
    Optional<StaticIPPool> findFirstAvailableByRegion(@Param("region") String region);

    // Find available IPs by region with pagination
    @Query("SELECT s FROM StaticIPPool s WHERE s.region = :region AND s.isAllocated = false")
    List<StaticIPPool> findAvailableByRegion(@Param("region") String region, org.springframework.data.domain.Pageable pageable);
}
