package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.StaticIPPlanType;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a user's static IP subscription plan.
 * Users can subscribe to different plans with varying numbers of regions.
 */
@Entity
@Table(name = "static_ip_subscription", indexes = {
    @Index(name = "idx_sips_user_id", columnList = "user_id"),
    @Index(name = "idx_sips_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaticIPSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 30)
    private StaticIPPlanType planType; // PERSONAL, PRO, MULTI_REGION, BUSINESS, ENTERPRISE

    @Column(name = "max_regions", nullable = false)
    private Integer maxRegions; // 1, 1, 3, 5, 10+

    @Column(name = "regions_included", nullable = false)
    @Builder.Default
    private Integer regionsIncluded = 1; // Number of regions included in the plan

    @Column(name = "regions_used", nullable = false)
    @Builder.Default
    private Integer regionsUsed = 0; // Number of regions currently used

    @Column(name = "port_forwards_per_region", nullable = false)
    @Builder.Default
    private Integer portForwardsPerRegion = 1; // Included port forwards per region

    @Column(name = "price_monthly", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", precision = 10, scale = 2)
    private BigDecimal priceYearly;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = true;

    @Column(name = "payment_gateway", length = 50)
    private String paymentGateway; // STRIPE, APPLE, GOOGLE, CRYPTO, etc.

    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId; // Stripe subscription ID, etc.

    @Column(name = "started_at")
    private LocalDateTime startedAt; // When the subscription started

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE &&
               (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
}
