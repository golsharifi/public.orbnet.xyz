package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.PartnerTier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a partner datacenter that hosts OrbMesh nodes.
 * Partners can earn revenue share and tokens from traffic served.
 */
@Entity
@Table(name = "orbmesh_partner", indexes = {
    @Index(name = "idx_omp_partner_uuid", columnList = "partner_uuid", unique = true),
    @Index(name = "idx_omp_tier", columnList = "tier"),
    @Index(name = "idx_omp_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrbMeshPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_uuid", nullable = false, unique = true, length = 36)
    private String partnerUuid;

    @Column(name = "partner_name", nullable = false, length = 255)
    private String partnerName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    // Partner tier
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    @Builder.Default
    private PartnerTier tier = PartnerTier.BRONZE;

    @Column(name = "revenue_share_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal revenueSharePercent = new BigDecimal("10.00");

    @Column(name = "token_bonus_multiplier", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal tokenBonusMultiplier = new BigDecimal("1.00");

    // Agreement
    @Column(name = "agreement_signed_at")
    private LocalDateTime agreementSignedAt;

    @Column(name = "agreement_expires_at")
    private LocalDateTime agreementExpiresAt;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // API credentials
    @Column(name = "api_key_hash", length = 255)
    private String apiKeyHash;

    @Column(name = "api_key_last_used_at")
    private LocalDateTime apiKeyLastUsedAt;

    // Statistics
    @Column(name = "total_nodes")
    @Builder.Default
    private Integer totalNodes = 0;

    @Column(name = "total_static_ips")
    @Builder.Default
    private Integer totalStaticIps = 0;

    @Column(name = "total_bandwidth_served_gb", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalBandwidthServedGb = BigDecimal.ZERO;

    @Column(name = "total_revenue_earned", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalRevenueEarned = BigDecimal.ZERO;

    @Column(name = "total_tokens_earned", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalTokensEarned = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
