package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.GatewayName;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking bandwidth addon purchases.
 * Similar to device addons, users can purchase additional bandwidth.
 */
@Entity
@Table(name = "bandwidth_addons", indexes = {
        @Index(name = "idx_bandwidth_addon_user", columnList = "user_id"),
        @Index(name = "idx_bandwidth_addon_subscription", columnList = "subscription_id"),
        @Index(name = "idx_bandwidth_addon_purchase_token", columnList = "purchase_token")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BandwidthAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private UserSubscription subscription;

    /**
     * Product ID from app store (e.g., "bandwidth_10gb", "bandwidth_50gb")
     */
    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    /**
     * Bandwidth amount in bytes
     */
    @Column(name = "bandwidth_bytes", nullable = false)
    private Long bandwidthBytes;

    /**
     * Price paid
     */
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Currency
     */
    @Column(name = "currency", length = 3)
    private String currency;

    /**
     * Payment gateway used
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gateway")
    private GatewayName gateway;

    /**
     * Purchase token from app store (for validation)
     */
    @Column(name = "purchase_token", length = 500)
    private String purchaseToken;

    /**
     * Order ID from app store
     */
    @Column(name = "order_id", length = 100)
    private String orderId;

    /**
     * Whether this addon has been applied to the subscription
     */
    @Column(name = "applied", nullable = false)
    @Builder.Default
    private Boolean applied = false;

    /**
     * When the addon was applied
     */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /**
     * Whether this is a promotional/free addon
     */
    @Column(name = "is_promotional")
    @Builder.Default
    private Boolean isPromotional = false;

    /**
     * Admin notes (for promotional addons)
     */
    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (applied == null) {
            applied = false;
        }
        if (isPromotional == null) {
            isPromotional = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Get bandwidth in GB for display
     */
    public double getBandwidthGB() {
        if (bandwidthBytes == null) {
            return 0.0;
        }
        return bandwidthBytes / (1024.0 * 1024.0 * 1024.0);
    }
}
