package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.GatewayName;
import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer paymentId;

    @Column(nullable = false)
    private String subscriptionId;

    @Column(nullable = false)
    private String purchaseToken;

    @Column(nullable = false)
    private Integer userId;

    @Column(nullable = false)
    private Integer groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GatewayName gateway;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @Builder
    public PendingSubscription(Integer paymentId, String subscriptionId, Integer userId,
            Integer groupId, GatewayName gateway, LocalDateTime createdAt) {
        this.paymentId = paymentId;
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.groupId = groupId;
        this.gateway = gateway;
        this.createdAt = createdAt;
    }
}
