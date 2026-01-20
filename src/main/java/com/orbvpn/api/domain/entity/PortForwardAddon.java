package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.PortForwardAddonType;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a purchased port forwarding addon pack.
 * Each addon provides extra port forwards for a specific region/allocation.
 */
@Entity
@Table(name = "port_forward_addon", indexes = {
    @Index(name = "idx_pfa_user_id", columnList = "user_id"),
    @Index(name = "idx_pfa_allocation_id", columnList = "allocation_id"),
    @Index(name = "idx_pfa_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortForwardAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    private StaticIPAllocation allocation; // Which static IP this addon is for

    @Enumerated(EnumType.STRING)
    @Column(name = "addon_type", nullable = false, length = 20)
    private PortForwardAddonType addonType;

    @Column(name = "extra_ports", nullable = false)
    private Integer extraPorts; // Number of extra ports this addon provides

    @Column(name = "ports_used", nullable = false)
    @Builder.Default
    private Integer portsUsed = 0; // Number of ports currently in use from this addon

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
    private String paymentGateway;

    @Column(name = "external_subscription_id", length = 255)
    private String externalSubscriptionId;

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

    public int getAvailablePorts() {
        return extraPorts - portsUsed;
    }
}
