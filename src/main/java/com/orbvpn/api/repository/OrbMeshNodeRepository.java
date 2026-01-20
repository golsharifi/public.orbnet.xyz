package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshNode;
import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.enums.DeploymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrbMeshNodeRepository extends JpaRepository<OrbMeshNode, Long> {

    // Find by node UUID
    Optional<OrbMeshNode> findByNodeUuid(String nodeUuid);

    // Find by deployment type
    List<OrbMeshNode> findByDeploymentType(DeploymentType deploymentType);

    // Find active nodes by deployment type
    @Query("SELECT n FROM OrbMeshNode n WHERE n.deploymentType = :type AND n.isActive = true AND n.online = true")
    List<OrbMeshNode> findActiveByDeploymentType(@Param("type") DeploymentType type);

    // Find by region
    List<OrbMeshNode> findByRegionAndIsActiveTrue(String region);

    // Find by partner
    List<OrbMeshNode> findByPartner(OrbMeshPartner partner);

    // Find active nodes by partner
    @Query("SELECT n FROM OrbMeshNode n WHERE n.partner = :partner AND n.isActive = true")
    List<OrbMeshNode> findActiveByPartner(@Param("partner") OrbMeshPartner partner);

    // Find nodes with static IP capability
    @Query("SELECT n FROM OrbMeshNode n WHERE n.supportsStaticIp = true AND n.isActive = true AND n.online = true")
    List<OrbMeshNode> findWithStaticIpCapability();

    // Find nodes with port forwarding capability
    @Query("SELECT n FROM OrbMeshNode n WHERE n.supportsPortForward = true AND n.isActive = true AND n.online = true")
    List<OrbMeshNode> findWithPortForwardCapability();

    // Find nodes needing heartbeat check (stale)
    @Query("SELECT n FROM OrbMeshNode n WHERE n.isActive = true AND n.lastHeartbeat < :threshold")
    List<OrbMeshNode> findStaleNodes(@Param("threshold") LocalDateTime threshold);

    // Update node online status
    @Modifying
    @Query("UPDATE OrbMeshNode n SET n.online = false WHERE n.lastHeartbeat < :threshold AND n.online = true")
    int markOfflineByHeartbeat(@Param("threshold") LocalDateTime threshold);

    // Count by deployment type
    @Query("SELECT n.deploymentType, COUNT(n) FROM OrbMeshNode n WHERE n.isActive = true GROUP BY n.deploymentType")
    List<Object[]> countByDeploymentType();

    // Count by region
    @Query("SELECT n.region, COUNT(n) FROM OrbMeshNode n WHERE n.isActive = true AND n.online = true GROUP BY n.region")
    List<Object[]> countOnlineByRegion();

    // Get total stats
    @Query("SELECT SUM(n.currentConnections), SUM(n.totalBandwidthServedGb) FROM OrbMeshNode n WHERE n.isActive = true")
    List<Object[]> getTotalStats();

    // Find nodes for load balancing (by region, ordered by load)
    @Query("SELECT n FROM OrbMeshNode n WHERE n.region = :region AND n.isActive = true AND n.online = true " +
           "AND n.currentConnections < n.maxConnections ORDER BY n.currentConnections ASC")
    List<OrbMeshNode> findAvailableByRegion(@Param("region") String region, org.springframework.data.domain.Pageable pageable);

    // Find best node for static IP (with capability and capacity)
    @Query("SELECT n FROM OrbMeshNode n WHERE n.region = :region AND n.isActive = true AND n.online = true " +
           "AND n.supportsStaticIp = true AND n.staticIpsUsed < n.staticIpsAvailable " +
           "ORDER BY (n.staticIpsAvailable - n.staticIpsUsed) DESC")
    List<OrbMeshNode> findBestForStaticIp(@Param("region") String region, org.springframework.data.domain.Pageable pageable);

    // Update connection count
    @Modifying
    @Query("UPDATE OrbMeshNode n SET n.currentConnections = n.currentConnections + :delta WHERE n.id = :nodeId")
    int updateConnectionCount(@Param("nodeId") Long nodeId, @Param("delta") int delta);

    // Find by public IP
    Optional<OrbMeshNode> findByPublicIp(String publicIp);

    // Count total active nodes
    long countByIsActiveTrue();

    // Count online nodes
    long countByIsActiveTrueAndOnlineTrue();

    // Find by partner ID with pagination
    @Query("SELECT n FROM OrbMeshNode n WHERE n.partner.id = :partnerId")
    Page<OrbMeshNode> findByPartnerId(@Param("partnerId") Long partnerId, org.springframework.data.domain.Pageable pageable);

    // Find by partner ID and online status
    @Query("SELECT n FROM OrbMeshNode n WHERE n.partner.id = :partnerId AND n.online = :online")
    List<OrbMeshNode> findByPartnerIdAndOnline(@Param("partnerId") Long partnerId, @Param("online") boolean online);

    // Find online nodes
    @Query("SELECT n FROM OrbMeshNode n WHERE n.isActive = true AND n.online = true ORDER BY n.currentConnections ASC")
    List<OrbMeshNode> findOnlineNodes(org.springframework.data.domain.Pageable pageable);
}
