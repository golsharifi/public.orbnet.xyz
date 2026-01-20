package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OrbMeshPartner;
import com.orbvpn.api.domain.enums.PartnerTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrbMeshPartnerRepository extends JpaRepository<OrbMeshPartner, Long> {

    // Find by UUID
    Optional<OrbMeshPartner> findByPartnerUuid(String partnerUuid);

    // Find by email
    Optional<OrbMeshPartner> findByContactEmail(String email);

    // Find active partners
    List<OrbMeshPartner> findByIsActiveTrue();

    // Find verified partners
    List<OrbMeshPartner> findByIsVerifiedTrueAndIsActiveTrue();

    // Find by tier
    List<OrbMeshPartner> findByTier(PartnerTier tier);

    // Count by tier
    @Query("SELECT p.tier, COUNT(p) FROM OrbMeshPartner p WHERE p.isActive = true GROUP BY p.tier")
    List<Object[]> countByTier();

    // Get total stats
    @Query("SELECT SUM(p.totalNodes), SUM(p.totalStaticIps), SUM(p.totalBandwidthServedGb) " +
           "FROM OrbMeshPartner p WHERE p.isActive = true")
    List<Object[]> getTotalStats();

    // Find partners with expiring agreements
    @Query("SELECT p FROM OrbMeshPartner p WHERE p.isActive = true " +
           "AND p.agreementExpiresAt BETWEEN CURRENT_TIMESTAMP AND :expiryDate")
    List<OrbMeshPartner> findWithExpiringAgreements(@Param("expiryDate") java.time.LocalDateTime expiryDate);

    // Verify API key (returns partner if key matches)
    @Query("SELECT p FROM OrbMeshPartner p WHERE p.partnerUuid = :partnerUuid " +
           "AND p.apiKeyHash = :apiKeyHash AND p.isActive = true AND p.isVerified = true")
    Optional<OrbMeshPartner> findByCredentials(
            @Param("partnerUuid") String partnerUuid,
            @Param("apiKeyHash") String apiKeyHash);

    // Count total active partners
    long countByIsActiveTrue();

    // Check if email exists
    boolean existsByContactEmail(String email);

    // Count active partners
    @Query("SELECT COUNT(p) FROM OrbMeshPartner p WHERE p.isActive = true")
    int countActivePartners();

    // Sum total bandwidth
    @Query("SELECT SUM(p.totalBandwidthServedGb) FROM OrbMeshPartner p WHERE p.isActive = true")
    java.math.BigDecimal sumTotalBandwidth();

    // Sum total tokens earned
    @Query("SELECT SUM(p.totalTokensEarned) FROM OrbMeshPartner p WHERE p.isActive = true")
    java.math.BigDecimal sumTotalTokensEarned();

    // Find by tier (paginated)
    org.springframework.data.domain.Page<OrbMeshPartner> findByTier(PartnerTier tier, org.springframework.data.domain.Pageable pageable);

    // Find top partners by bandwidth
    @Query("SELECT p FROM OrbMeshPartner p WHERE p.isActive = true " +
           "ORDER BY p.totalBandwidthServedGb DESC")
    List<OrbMeshPartner> findTopByBandwidth(org.springframework.data.domain.Pageable pageable);

    // Find by active status (paginated)
    org.springframework.data.domain.Page<OrbMeshPartner> findByIsActive(Boolean isActive, org.springframework.data.domain.Pageable pageable);

    // Find by UUID prefix (for API key lookup)
    @Query("SELECT p FROM OrbMeshPartner p WHERE p.partnerUuid LIKE CONCAT(:prefix, '%') AND p.isActive = true")
    List<OrbMeshPartner> findByPartnerUuidStartingWith(@Param("prefix") String prefix);
}
